package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> PROGRAM_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "name",
            "handle",
            "state",
            "submissionState"
    );
    private static final Set<String> PROGRAM_UPDATE_SORT_PROPERTIES =
            Set.of("id", "createdAt");

    private final ProgramRepository programRepository;
    private final ProgramAssetRepository programAssetRepository;
    private final ProgramUpdateRepository programUpdateRepository;
    private final ProgramMapper mapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final FollowNotificationService followNotificationService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramSummaryResponseDto> getPublicPrograms(
            UUID organizationId,
            EngagementType engagementType,
            Boolean offersBounties,
            Pageable pageable
    ) {
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.publicPrograms(
                        organizationId,
                        engagementType,
                        offersBounties
                ),
                validatedPageable
        );
        PublicProgramContext context = loadPublicProgramContext(
                programs.getContent()
        );
        return programs.map(program -> mapper.toSummaryDto(
                program,
                context.organizations().get(program.getOrganizationId()),
                context.assetsByProgram().getOrDefault(
                        program.getId(),
                        List.of()
                )
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramById(UUID id) {
        return toPublicResponse(findPublicProgramById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramByHandle(String handle) {
        Program program = programRepository
                .findByHandle(normalizeHandle(handle))
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
        return toPublicResponse(program);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramManagementSummaryResponseDto> getMyPrograms(
            Pageable pageable
    ) {
        Organization organization = findAccessibleOrganization(
                OrganizationPermission.VIEW_PROGRAMS
        );
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.organizationPrograms(
                        organization.getId()
                ),
                validatedPageable
        );
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                loadProgramAssets(programs.getContent());
        return programs.map(program -> mapper.toManagementSummaryDto(
                program,
                organization,
                assetsByProgram.getOrDefault(program.getId(), List.of())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getMyProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.VIEW_PROGRAMS
        );
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto createProgram(ProgramRequestDto request) {
        Organization organization = findAccessibleOrganization(
                OrganizationPermission.CREATE_PROGRAM
        );
        requireUniqueHandle(request.handle(), null);

        Program program = mapper.toEntity(request);
        program.setOrganizationId(organization.getId());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        validateProgramConfiguration(program);

        Program saved = programRepository.saveAndFlush(program);
        logUpdate(saved, "Program created and submitted for admin review");
        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateProgram(
            UUID id,
            ProgramUpdateRequestDto request
    ) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.EDIT_PROGRAM
        );
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Closed programs cannot be updated");
        }
        if (!hasChanges(request)) {
            throw badRequest("At least one program field must be updated");
        }

        if (request.handle() != null) {
            requireUniqueHandle(request.handle(), program.getId());
        }

        boolean requiresNewReview =
                program.getSubmissionState() == SubmissionState.APPROVED
                        && hasReviewSensitiveChanges(request);
        boolean becomingPublic = program.getVisibility() != Visibility.PUBLIC
                && request.visibility() == Visibility.PUBLIC;
        mapper.updateEntity(request, program);
        validateProgramConfiguration(program);
        if (requiresNewReview) {
            program.setSubmissionState(SubmissionState.PENDING_REVIEW);
            program.setState(ProgramState.DRAFT);
            program.setVisibility(Visibility.PRIVATE);
            logUpdate(
                    program,
                    "Program details updated; admin approval requested again"
            );
        } else if (request.visibility() != null
                && !hasReviewSensitiveChanges(request)) {
            logUpdate(
                    program,
                    "Program visibility changed to "
                            + request.visibility()
                            .name()
                            .toLowerCase(Locale.ROOT)
            );
        } else {
            logUpdate(program, "Program details updated");
        }
        if (becomingPublic && isPubliclyAccessible(program)) {
            notifyOrganizationFollowersOfPublishedProgram(program);
        }
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto submitProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.EDIT_PROGRAM
        );
        if (program.getSubmissionState() != SubmissionState.REJECTED) {
            throw conflict(
                    "Only rejected programs can be resubmitted for review"
            );
        }

        validateProgramConfiguration(program);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(program, "Program resubmitted for admin review");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto publishProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() != ProgramState.DRAFT) {
            throw conflict("Only draft programs can be launched");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved by an admin before launch"
            );
        }

        program.setState(ProgramState.ACTIVE);
        logUpdate(program, "Program launched");
        if (program.getVisibility() == Visibility.PUBLIC) {
            notifyOrganizationFollowersOfPublishedProgram(program);
        }
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto pauseProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() != ProgramState.ACTIVE) {
            throw conflict("Only active programs can be paused");
        }

        program.setState(ProgramState.PAUSED);
        logUpdate(program, "Program paused");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto resumeProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() != ProgramState.PAUSED) {
            throw conflict("Only paused programs can be resumed");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved before it can be resumed"
            );
        }

        program.setState(ProgramState.ACTIVE);
        logUpdate(program, "Program resumed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto closeProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Program is already closed");
        }

        program.setState(ProgramState.CLOSED);
        logUpdate(program, "Program closed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public void deleteProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.DELETE_PROGRAM
        );
        program.setState(ProgramState.CLOSED);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(program, "Program deleted");
        program.setDeletedAt(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramUpdateChangeLogDto> getPublicProgramUpdates(
            UUID id,
            Pageable pageable
    ) {
        findPublicProgramById(id);
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_UPDATE_SORT_PROPERTIES
        );
        return programUpdateRepository
                .findByProgramId(id, validatedPageable)
                .map(mapper::toUpdateDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramManagementSummaryResponseDto> getProgramsForReview(
            SubmissionState submissionState,
            Pageable pageable
    ) {
        requireRole(ADMIN_ROLE);
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.programsForReview(submissionState),
                validatedPageable
        );
        Map<UUID, Organization> organizationsById = loadOrganizationsById(
                programs.getContent()
        );
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                loadProgramAssets(programs.getContent());
        return programs.map(program -> mapper.toManagementSummaryDto(
                program,
                organizationsById.get(program.getOrganizationId()),
                assetsByProgram.getOrDefault(program.getId(), List.of())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getProgramForAdmin(UUID id) {
        requireRole(ADMIN_ROLE);
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto approveProgram(UUID id) {
        requireRole(ADMIN_ROLE);
        Program program = findPendingProgramForReview(id);
        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found for this program"
                ));
        requireApprovedOrganization(organization);
        validateProgramConfiguration(program);

        program.setSubmissionState(SubmissionState.APPROVED);
        logUpdate(program, "Program approved by admin");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto rejectProgram(UUID id, String reason) {
        requireRole(ADMIN_ROLE);
        Program program = findPendingProgramForReview(id);

        program.setSubmissionState(SubmissionState.REJECTED);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(
                program,
                "Program rejected by admin: " + reason.trim()
        );
        return mapper.toResponseDto(program);
    }

    private Program findPublicProgramById(UUID id) {
        return programRepository.findById(id)
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
    }

    private Program findProgramForManagement(
            UUID id,
            OrganizationPermission permission
    ) {
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        organizationAuthorization.requirePermission(
                program.getOrganizationId(),
                extractCurrentUserId(),
                permission
        );
        return program;
    }

    private Program findPendingProgramForReview(UUID id) {
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        if (program.getSubmissionState() != SubmissionState.PENDING_REVIEW) {
            throw conflict("Program is not pending admin review");
        }
        return program;
    }

    private Organization findAccessibleOrganization(
            OrganizationPermission permission
    ) {
        return organizationAuthorization.findSingleAccessibleOrganization(
                extractCurrentUserId(),
                permission
        );
    }

    private Organization requireApprovedOrganization(
            Organization organization
    ) {
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw conflict(
                    "Organization must be approved before managing programs"
            );
        }
        return organization;
    }

    private boolean isPubliclyAccessible(Program program) {
        return program.getDeletedAt() == null
                && program.getState() == ProgramState.ACTIVE
                && program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC;
    }

    private void requireUniqueHandle(String handle, UUID excludedId) {
        String normalizedHandle = normalizeHandle(handle);
        boolean exists = excludedId == null
                ? programRepository.existsByHandleIgnoreCase(normalizedHandle)
                : programRepository.existsByHandleIgnoreCaseAndIdNot(
                        normalizedHandle,
                        excludedId
                );
        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Program handle is already in use"
            );
        }
    }

    private void validateProgramConfiguration(Program program) {
        if (program.getPolicy() == null || program.getPolicy().isBlank()) {
            throw badRequest("Program policy is required");
        }
        if (program.getProofOfConceptRequirements() == null
                || program.getProofOfConceptRequirements().isBlank()) {
            throw badRequest("Proof of concept requirements are required");
        }
        validateGuidelines(
                "Rules of engagement",
                program.getRulesOfEngagement()
        );
        validateGuidelines("Program exclusions", program.getExclusions());
        if (program.getAssets().isEmpty()) {
            throw badRequest("At least one program asset is required");
        }

        validateRange(
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                "Program bounty"
        );
        validateAssets(program.getAssets());
        validateRewards(program);
    }

    private boolean hasChanges(ProgramUpdateRequestDto request) {
        return request.handle() != null
                || request.name() != null
                || request.description() != null
                || request.engagementType() != null
                || request.visibility() != null
                || request.policy() != null
                || request.proofOfConceptRequirements() != null
                || request.rulesOfEngagement() != null
                || request.exclusions() != null
                || request.offersBounties() != null
                || request.minimumBounty() != null
                || request.maximumBounty() != null
                || request.assets() != null
                || request.rewards() != null;
    }

    private boolean hasReviewSensitiveChanges(
            ProgramUpdateRequestDto request
    ) {
        return request.handle() != null
                || request.name() != null
                || request.description() != null
                || request.engagementType() != null
                || request.policy() != null
                || request.proofOfConceptRequirements() != null
                || request.rulesOfEngagement() != null
                || request.exclusions() != null
                || request.offersBounties() != null
                || request.minimumBounty() != null
                || request.maximumBounty() != null
                || request.assets() != null
                || request.rewards() != null;
    }

    private void validateAssets(List<ProgramAsset> assets) {
        Set<String> uniqueAssets = new HashSet<>();
        for (ProgramAsset asset : assets) {
            String key = asset.getAssetType()
                    + ":"
                    + asset.getIdentifier().trim().toLowerCase(Locale.ROOT);
            if (!uniqueAssets.add(key)) {
                throw badRequest(
                        "Program assets must be unique by type and identifier"
                );
            }
        }
    }

    private void validateRewards(Program program) {
        if (Boolean.TRUE.equals(program.getOffersBounties())
                && program.getRewards().isEmpty()) {
            throw badRequest(
                    "At least one reward tier is required when bounties are enabled"
            );
        }

        Set<Object> severities = new HashSet<>();
        for (ProgramReward reward : program.getRewards()) {
            if (!severities.add(reward.getSeverity())) {
                throw badRequest(
                        "Only one reward tier is allowed per severity"
                );
            }
            validateRange(
                    reward.getMinAmount(),
                    reward.getMaxAmount(),
                    "Reward amount"
            );
        }
    }

    private void validateRange(
            BigDecimal minimum,
            BigDecimal maximum,
            String label
    ) {
        if (minimum != null
                && maximum != null
                && maximum.compareTo(minimum) < 0) {
            throw badRequest(
                    label + " maximum must be greater than or equal to minimum"
            );
        }
    }

    private void logUpdate(Program program, String summary) {
        UUID actorId = extractCurrentUserId();
        ProgramUpdate update = programUpdateRepository.save(
                ProgramUpdate.builder()
                .program(program)
                .changeSummary(summary)
                .changedBy(actorId)
                .build()
        );
        if (program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC) {
            followNotificationService.notifyFollowers(
                    FollowType.PROGRAM,
                    program.getId(),
                    actorId,
                    "Program updated",
                    summary,
                    NotificationType.PROGRAM,
                    program.getId(),
                    "program-update:" + update.getId()
            );
        }
    }

    private void validateGuidelines(
            String fieldName,
            ProgramGuidelinesDto guidelines
    ) {
        if (guidelines == null) {
            throw badRequest(fieldName + " are required");
        }
        if (guidelines.description() == null
                || guidelines.description().isBlank()) {
            throw badRequest(fieldName + " description is required");
        }
        if (guidelines.description().length() > 2000) {
            throw badRequest(
                    fieldName + " description cannot exceed 2000 characters"
            );
        }
        if (guidelines.rules() == null || guidelines.rules().isEmpty()) {
            throw badRequest(fieldName + " must contain at least one rule");
        }
        if (guidelines.rules().size() > 50) {
            throw badRequest(fieldName + " cannot contain more than 50 rules");
        }
        boolean invalidRule = guidelines.rules().stream()
                .anyMatch(rule -> rule == null
                        || rule.isBlank()
                        || rule.length() > 500);
        if (invalidRule) {
            throw badRequest(
                    fieldName
                            + " rules must be non-blank and cannot exceed 500 characters"
            );
        }
    }

    private PublicProgramResponseDto toPublicResponse(Program program) {
        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(this::programNotFound);
        List<ProgramAsset> assets = programAssetRepository
                .findByProgramIdOrderByCreatedAtAsc(program.getId());
        ProgramRepository.PublicProgramStatistics statistics =
                programRepository.findPublicStatisticsByProgramId(
                        program.getId()
                );
        return mapper.toPublicResponseDto(
                program,
                organization,
                assets,
                statistics.getTotalResearchers(),
                statistics.getTotalSubmissions()
        );
    }

    private PublicProgramContext loadPublicProgramContext(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return new PublicProgramContext(Map.of(), Map.of());
        }

        // The whole row, not just the name: the public listing now carries the
        // organization's profile, and this query already had to load it.
        Map<UUID, Organization> organizations = loadOrganizationsById(programs);

        Set<UUID> programIds = programs.stream()
                .map(Program::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                programAssetRepository
                        .findInScopeByProgramIds(programIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                asset -> asset.getProgram().getId()
                        ));

        return new PublicProgramContext(
                organizations,
                assetsByProgram
        );
    }

    private Map<UUID, Organization> loadOrganizationsById(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        Set<UUID> organizationIds = programs.stream()
                .map(Program::getOrganizationId)
                .collect(Collectors.toSet());
        return organizationRepository.findAllById(organizationIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getId,
                        organization -> organization
                ));
    }

    private Map<UUID, List<ProgramAsset>> loadProgramAssets(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        Set<UUID> programIds = programs.stream()
                .map(Program::getId)
                .collect(Collectors.toSet());
        return programAssetRepository.findByProgramIds(programIds)
                .stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getProgram().getId()
                ));
    }

    private record PublicProgramContext(
            Map<UUID, Organization> organizations,
            Map<UUID, List<ProgramAsset>> assetsByProgram
    ) {
    }

    private void notifyOrganizationFollowersOfPublishedProgram(
            Program program
    ) {
        followNotificationService.notifyFollowers(
                FollowType.ORGANIZATION,
                program.getOrganizationId(),
                extractCurrentUserId(),
                "New program published",
                program.getName(),
                NotificationType.PROGRAM,
                program.getId(),
                "program-published:" + program.getId()
        );
    }

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + role
            );
        }
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private String normalizeHandle(String handle) {
        return handle.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResourceNotFoundException programNotFound() {
        return new ResourceNotFoundException("Program not found");
    }
}
