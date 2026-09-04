package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SaveSolutionDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
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
public class SolutionDraftServiceImpl implements SolutionDraftService {

    private static final String USER_ROLE = "USER";

    /**
     * A cap rather than a limit anyone should reach. Drafts are free to create
     * and never expire, so without one an autosave bug on the client turns into
     * unbounded rows.
     */
    private static final int MAX_DRAFTS = 20;

    private static final Set<String> DRAFT_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "summary"
    );

    private final SolutionDraftRepository solutionDraftRepository;
    private final UserProfileRepository userProfileRepository;
    private final SolutionService solutionService;
    private final Validator validator;

    @Override
    @Transactional
    public SolutionDraftResponse create(
            UUID problemId,
            SaveSolutionDraftRequest request
    ) {
        requireRole(USER_ROLE);
        UUID authorId = currentUserId();

        long existing = solutionDraftRepository.countByAuthor_Id(authorId);
        if (existing >= MAX_DRAFTS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have " + MAX_DRAFTS + " solution drafts. "
                            + "Post or delete one before starting another."
            );
        }

        SolutionDraft draft = SolutionDraft.builder()
                .author(findUserProfile(authorId))
                .problemId(problemId)
                .build();
        apply(draft, request);
        return toResponse(solutionDraftRepository.save(draft));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionDraftResponse> findMine(
            UUID problemId,
            Pageable pageable
    ) {
        requireRole(USER_ROLE);
        UUID authorId = currentUserId();
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                DRAFT_SORT_PROPERTIES
        );
        Page<SolutionDraft> page = problemId == null
                ? solutionDraftRepository.findByAuthor_Id(authorId, validated)
                : solutionDraftRepository.findByAuthor_IdAndProblemId(
                        authorId,
                        problemId,
                        validated
                );
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SolutionDraftResponse findById(UUID id) {
        return toResponse(findOwnDraft(id));
    }

    @Override
    @Transactional
    public SolutionDraftResponse save(
            UUID id,
            SaveSolutionDraftRequest request
    ) {
        SolutionDraft draft = findOwnDraft(id);
        apply(draft, request);
        return toResponse(solutionDraftRepository.save(draft));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        solutionDraftRepository.delete(findOwnDraft(id));
    }

    @Override
    @Transactional
    public SolutionResponse submit(UUID id) {
        SolutionDraft draft = findOwnDraft(id);
        SolutionRequest request = toSolutionRequest(draft);

        // The constraints on SolutionRequest normally run at the controller,
        // via @Valid. Submitting reaches the service directly, so they are run
        // here instead of restated — a draft must not be able to post a
        // solution that a direct post would have been refused. This is where
        // the minimums bite: a ten-character summary and a thirty-character
        // body are required of a solution and deliberately not of a draft.
        Set<ConstraintViolation<SolutionRequest>> violations =
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

        // Everything past this point is the ordinary posting path, so the
        // problem lookup, its state and the duplicate check all behave exactly
        // as they always did. Sharing one transaction means a refusal leaves
        // the draft untouched.
        SolutionResponse response = solutionService.createSolution(
                draft.getProblemId(),
                request
        );
        solutionDraftRepository.delete(draft);
        return response;
    }

    private void apply(SolutionDraft draft, SaveSolutionDraftRequest request) {
        draft.setSummary(request.summary());
        draft.setBodyMarkdown(request.bodyMarkdown());
        draft.setApproachType(request.approachType());
        draft.setVerificationSteps(request.verificationSteps());
        draft.setTestedWith(request.testedWith());
        draft.setTradeoffs(request.tradeoffs());
        draft.setResources(request.resources());
    }

    private SolutionRequest toSolutionRequest(SolutionDraft draft) {
        return new SolutionRequest(
                draft.getSummary(),
                draft.getBodyMarkdown(),
                draft.getApproachType(),
                draft.getVerificationSteps(),
                draft.getTestedWith(),
                draft.getTradeoffs(),
                draft.getResources()
        );
    }

    private SolutionDraftResponse toResponse(SolutionDraft draft) {
        return new SolutionDraftResponse(
                draft.getId(),
                draft.getAuthor().getId(),
                draft.getProblemId(),
                draft.getSummary(),
                draft.getBodyMarkdown(),
                draft.getApproachType(),
                copyOrNull(draft.getVerificationSteps()),
                copyOrNull(draft.getTestedWith()),
                draft.getTradeoffs(),
                copyOrNull(draft.getResources()),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private <T> List<T> copyOrNull(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }

    private SolutionDraft findOwnDraft(UUID id) {
        requireRole(USER_ROLE);
        return solutionDraftRepository
                .findByIdAndAuthor_Id(id, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft not found"
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
