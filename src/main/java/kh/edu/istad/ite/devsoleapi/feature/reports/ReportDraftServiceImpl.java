package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SaveReportDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportDraft;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportDraftServiceImpl implements ReportDraftService {

    private static final String USER_ROLE = "USER";

    /**
     * A cap rather than a limit anyone should reach. Drafts are free to create
     * and never expire, so without one an autosave bug on the client turns
     * into unbounded rows.
     */
    private static final int MAX_DRAFTS_PER_PROGRAM = 20;

    private static final Set<String> DRAFT_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "title"
    );

    private final ReportDraftRepository reportDraftRepository;
    private final ProgramRepository programRepository;
    private final UserProfileRepository userProfileRepository;
    private final ReportService reportService;
    private final Validator validator;

    @Override
    @Transactional
    public ReportDraftResponse create(
            UUID programId,
            SaveReportDraftRequest request
    ) {
        requireRole(USER_ROLE);
        UUID reporterId = currentUserId();
        UserProfile reporter = findUserProfile(reporterId);
        Program program = findDraftableProgram(programId);

        long existing = reportDraftRepository
                .countByReporterIdAndProgramId(reporterId, programId);
        if (existing >= MAX_DRAFTS_PER_PROGRAM) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have " + MAX_DRAFTS_PER_PROGRAM
                            + " drafts for this program. Submit or delete one "
                            + "before starting another."
            );
        }

        ReportDraft draft = ReportDraft.builder()
                .program(program)
                .reporter(reporter)
                .build();
        apply(draft, request);
        return toResponse(reportDraftRepository.save(draft));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportDraftResponse> findMine(
            UUID programId,
            Pageable pageable
    ) {
        requireRole(USER_ROLE);
        UUID reporterId = currentUserId();
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                DRAFT_SORT_PROPERTIES
        );
        Page<ReportDraft> page = programId == null
                ? reportDraftRepository.findByReporterId(reporterId, validated)
                : reportDraftRepository.findByReporterIdAndProgramId(
                        reporterId,
                        programId,
                        validated
                );
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportDraftResponse findById(UUID id) {
        return toResponse(findOwnDraft(id));
    }

    @Override
    @Transactional
    public ReportDraftResponse save(UUID id, SaveReportDraftRequest request) {
        ReportDraft draft = findOwnDraft(id);
        apply(draft, request);
        return toResponse(reportDraftRepository.save(draft));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        reportDraftRepository.delete(findOwnDraft(id));
    }

    @Override
    @Transactional
    public ReportResponse submit(UUID id) {
        ReportDraft draft = findOwnDraft(id);
        CreateReportRequest request = toCreateRequest(draft);

        // The constraints on CreateReportRequest normally run at the
        // controller, via @Valid. Submitting reaches the service directly, so
        // they are run here instead of restated — a draft must not be able to
        // file a report that a direct submission would have been refused.
        Set<ConstraintViolation<CreateReportRequest>> violations =
                validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    violations.stream()
                            .map(ConstraintViolation::getMessage)
                            .sorted()
                            .collect(Collectors.joining("; "))
            );
        }

        // Everything past this point is the ordinary submission path, so scope,
        // program state, a retired weakness and a CVSS score that disagrees
        // with its severity are all caught the way they always were. Sharing
        // one transaction means a refusal leaves the draft untouched.
        ReportResponse response = reportService.create(
                draft.getProgram().getId(),
                request
        );
        reportDraftRepository.delete(draft);
        return response;
    }

    private void apply(ReportDraft draft, SaveReportDraftRequest request) {
        draft.setTitle(request.title());
        draft.setVulnerabilityInformation(request.vulnerabilityInformation());
        draft.setImpact(request.impact());
        draft.setStepsToReproduce(request.stepsToReproduce());
        draft.setProofOfConcept(request.proofOfConcept());
        draft.setRemediationRecommendation(
                request.remediationRecommendation()
        );
        draft.setTargetEndpoint(request.targetEndpoint());
        draft.setEnvironment(request.environment());
        draft.setDiscoveredAt(request.discoveredAt());
        draft.setReferenceLinks(request.referenceLinks());
        draft.setReportedSeverity(request.reportedSeverity());
        draft.setCvssVector(request.cvssVector());
        draft.setCvssScore(request.cvssScore());
        draft.setWeaknessId(request.weaknessId());
        draft.setAssetId(request.assetId());
    }

    private CreateReportRequest toCreateRequest(ReportDraft draft) {
        return new CreateReportRequest(
                draft.getTitle(),
                draft.getVulnerabilityInformation(),
                draft.getImpact(),
                draft.getStepsToReproduce(),
                draft.getProofOfConcept(),
                draft.getRemediationRecommendation(),
                draft.getTargetEndpoint(),
                draft.getEnvironment(),
                draft.getDiscoveredAt(),
                draft.getReferenceLinks(),
                draft.getReportedSeverity(),
                draft.getCvssVector(),
                draft.getCvssScore(),
                draft.getWeaknessId(),
                draft.getAssetId()
        );
    }

    private ReportDraftResponse toResponse(ReportDraft draft) {
        List<String> links = draft.getReferenceLinks();
        return new ReportDraftResponse(
                draft.getId(),
                draft.getProgram().getId(),
                draft.getTitle(),
                draft.getVulnerabilityInformation(),
                draft.getImpact(),
                draft.getStepsToReproduce(),
                draft.getProofOfConcept(),
                draft.getRemediationRecommendation(),
                draft.getTargetEndpoint(),
                draft.getEnvironment(),
                draft.getDiscoveredAt(),
                links == null ? null : List.copyOf(links),
                draft.getReportedSeverity(),
                draft.getCvssVector(),
                draft.getCvssScore(),
                draft.getWeaknessId(),
                draft.getAssetId(),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private ReportDraft findOwnDraft(UUID id) {
        requireRole(USER_ROLE);
        return reportDraftRepository
                .findByIdAndReporterId(id, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft not found"
                ));
    }

    /**
     * Only that the program exists and has not been deleted. Whether it is
     * still accepting reports is checked at submit, by the create path: a
     * programme that pauses for a week should not cost a reporter the draft
     * they were part-way through.
     */
    private Program findDraftableProgram(UUID programId) {
        return programRepository.findById(programId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program not found"
                ));
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user profile not found"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated subject is not a user id"
            );
        }
    }

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + role
            );
        }
    }
}
