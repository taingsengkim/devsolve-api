package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemAcceptedSolution;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.AcceptedSolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResourceRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.TestedWithRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.VerificationStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.Vote;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteSummaryProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolutionServiceImpl implements SolutionService {

    private final SolutionResponseEnricher solutionResponseEnricher;

    private static final int MAX_ATTACHMENTS = 10;
    private static final Duration DOWNLOAD_LINK_VALIDITY = Duration.ofMinutes(5);
    private static final Pattern DANGEROUS_MARKUP = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed|style)\\b.*?<\\s*/\\s*\\1\\s*>"
    );
    private static final Pattern EVENT_HANDLER = Pattern.compile(
            "(?i)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1"
    );
    private static final Pattern JAVASCRIPT_SCHEME = Pattern.compile(
            "(?i)javascript\\s*:"
    );

    private final SolutionRepository solutionRepository;
    private final SolutionRevisionRepository revisionRepository;
    private final SolutionAttachmentRepository attachmentRepository;
    private final ProblemRepository problemRepository;
    private final ProblemService problemService;
    private final UserProfileRepository userProfileRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;
    private final AttachmentValidator attachmentValidator;
    private final ObjectStorageService objectStorageService;
    private final FollowNotificationService followNotificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SolutionResponse createSolution(UUID problemId, SolutionRequest request) {
        Problem problem = problemRepository.findPublicByIdForUpdate(problemId)
                .orElseThrow(() -> notFound("Problem", problemId));
        if (problem.getStatus() != ProblemStatus.PUBLISHED
                && problem.getStatus() != ProblemStatus.RESOLVED) {
            throw conflict(
                    "Solutions can only be submitted to published or resolved problems"
            );
        }

        UUID authorId = requireCurrentUserId();
        requireAuthorProfile(authorId);
        Solution solution = solutionRepository.saveAndFlush(
                Solution.builder()
                        .problem(problem)
                        .authorId(authorId)
                        .build()
        );
        SolutionRevision revision = revisionRepository.saveAndFlush(
                createRevision(solution, 1, request)
        );
        solution.setLatestRevision(revision);
        solution = solutionRepository.saveAndFlush(solution);
        return toResponse(solution, revision, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getSolutionsByProblemId(
            UUID problemId,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);
        problemRepository.findPublicById(problemId)
                .orElseThrow(() -> notFound("Problem", problemId));
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );
        return toResponses(
                solutionRepository
                        .findAllByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                                problemId,
                                pageable
                        ),
                Solution::getCurrentPublishedRevision,
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SolutionResponse getById(UUID solutionId) {
        Solution solution = solutionRepository
                .findByIdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(solutionId)
                .orElseThrow(() -> notFound("Solution", solutionId));
        return toResponse(solution, solution.getCurrentPublishedRevision(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getMine(int pageNumber, int pageSize) {
        validatePagination(pageNumber, pageSize);
        UUID authorId = requireCurrentUserId();
        return toResponses(
                solutionRepository.findAllByAuthorIdAndDeletedAtIsNull(
                        authorId,
                        PageRequest.of(
                                pageNumber,
                                pageSize,
                                Sort.by(Sort.Direction.DESC, "updatedAt")
                        )
                ),
                Solution::getLatestRevision,
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getPublicByAuthor(
            UUID authorId,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);
        return toResponses(
                solutionRepository
                        .findAllByAuthorIdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                                authorId,
                                PageRequest.of(
                                        pageNumber,
                                        pageSize,
                                        Sort.by(Sort.Direction.DESC, "createdAt")
                                )
                        ),
                Solution::getCurrentPublishedRevision,
                false
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getForModeration(
            ReviewStatus reviewStatus,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin();
        validatePagination(pageNumber, pageSize);
        return toResponses(
                solutionRepository.findForModeration(
                        reviewStatus,
                        PageRequest.of(
                                pageNumber,
                                pageSize,
                                Sort.by(Sort.Direction.ASC, "createdAt")
                        )
                ),
                Solution::getLatestRevision,
                true
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SolutionResponse getAdminById(UUID solutionId) {
        requireAdmin();
        Solution solution = findActiveSolution(solutionId);
        return toResponse(solution, solution.getLatestRevision(), true);
    }

    @Override
    @Transactional
    public SolutionResponse updateSolution(
            UUID id,
            SolutionUpdateRequest request,
            long expectedVersion
    ) {
        return updateSolution(findActiveSolution(id), request, expectedVersion);
    }

    private SolutionResponse updateSolution(
            Solution solution,
            SolutionUpdateRequest request,
        long expectedVersion
    ) {
        requireAuthor(solution, requireCurrentUserId());
        requireVersion(solution, expectedVersion);
        validateUpdateRequest(request);
        SolutionRevision previous = requireLatestRevision(solution);
        SolutionRevision revision = revisionRepository.saveAndFlush(
                createRevision(solution, previous.getRevisionNumber() + 1, request, previous)
        );
        solution.setLatestRevision(revision);
        Solution saved = solutionRepository.saveAndFlush(solution);
        return toResponse(saved, revision, true);
    }

    @Override
    @Transactional
    public void deleteSolution(UUID id) {
        UUID problemId = solutionRepository.findActiveProblemId(id)
                .orElseThrow(() -> notFound("Solution", id));
        // Every workflow touching both rows locks the parent first. Keeping a
        // single lock order prevents problem deletion and solution deletion
        // from waiting on each other in opposite directions.
        Problem problem = problemRepository.findActiveByIdForUpdate(problemId)
                .orElseThrow(() -> notFound("Problem", problemId));
        Solution solution = solutionRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> notFound("Solution", id));
        if (!problemId.equals(solution.getProblem().getId())) {
            throw conflict("The solution no longer belongs to this problem");
        }
        UUID userId = requireCurrentUserId();
        boolean admin = AuthUtils.hasRole("ADMIN");
        if (!admin && !solution.getAuthorId().equals(userId)) {
            throw forbidden("You are not allowed to delete this solution");
        }
        Optional<ProblemAcceptedSolution> acceptance = findAcceptance(
                problem,
                solution.getId()
        );
        if (acceptance.isPresent()) {
            if (!admin) {
                throw conflict("Remove acceptance before deleting this solution");
            }
            problem.getAcceptedSolutions().remove(acceptance.get());
            reopenIfNoAcceptedSolutions(problem);
            problemRepository.saveAndFlush(problem);
        }
        solution.setDeletedAt(LocalDateTime.now());
        solutionRepository.saveAndFlush(solution);
    }

    @Override
    @Transactional
    public ProblemResponse setAcceptedSolution(
            UUID problemId,
            AcceptedSolutionRequest request
    ) {
        Problem problem = problemRepository.findActiveByIdForUpdate(problemId)
                .orElseThrow(() -> notFound("Problem", problemId));
        requireAcceptancePermission(problem);
        Solution solution = solutionRepository.findActiveByIdForUpdate(request.solutionId())
                .orElseThrow(() -> notFound("Solution", request.solutionId()));
        if (!problemId.equals(solution.getProblem().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The solution does not belong to this problem"
            );
        }
        SolutionRevision published = solution.getCurrentPublishedRevision();
        if (published == null || published.getModerationStatus() != ReviewStatus.APPROVED) {
            throw conflict("Only an approved solution can be accepted");
        }
        if (findAcceptance(problem, solution.getId()).isEmpty()) {
            problem.getAcceptedSolutions().add(
                    ProblemAcceptedSolution.builder()
                            .problem(problem)
                            .solutionId(solution.getId())
                            .acceptedBy(requireCurrentUserId())
                            .build()
            );
            problem.setStatus(ProblemStatus.RESOLVED);
            problemRepository.saveAndFlush(problem);
        }
        return problemService.findById(problemId);
    }

    @Override
    @Transactional
    public ProblemResponse removeAcceptedSolution(
            UUID problemId,
            UUID solutionId
    ) {
        Problem problem = problemRepository.findActiveByIdForUpdate(problemId)
                .orElseThrow(() -> notFound("Problem", problemId));
        requireAcceptancePermission(problem);
        Optional<ProblemAcceptedSolution> acceptance = findAcceptance(
                problem,
                solutionId
        );
        if (acceptance.isPresent()) {
            problem.getAcceptedSolutions().remove(acceptance.get());
            reopenIfNoAcceptedSolutions(problem);
            problemRepository.saveAndFlush(problem);
        }
        return problemService.findById(problemId);
    }

    @Override
    @Transactional
    public SolutionResponse updateReviewStatus(
            UUID solutionId,
            UpdateSolutionReviewStatusRequest request
    ) {
        requireAdmin();
        validateReviewRequest(request);
        Solution solution = solutionRepository.findActiveByIdForUpdate(solutionId)
                .orElseThrow(() -> notFound("Solution", solutionId));
        SolutionRevision revision = requireLatestRevision(solution);
        if (revision.getModerationStatus() != ReviewStatus.PENDING) {
            throw conflict("Only a pending solution revision can be reviewed");
        }

        UUID reviewerId = requireCurrentUserId();
        revision.setModerationStatus(request.reviewStatus());
        revision.setReviewedBy(reviewerId);
        revision.setReviewedAt(LocalDateTime.now());
        revision.setRejectionReason(
                request.reviewStatus() == ReviewStatus.REJECTED
                        ? request.rejectionReason().trim()
                        : null
        );
        revisionRepository.saveAndFlush(revision);
        if (request.reviewStatus() == ReviewStatus.APPROVED) {
            solution.setCurrentPublishedRevision(revision);
            solutionRepository.saveAndFlush(solution);
            followNotificationService.notifyFollowers(
                    FollowType.PROBLEM,
                    solution.getProblem().getId(),
                    reviewerId,
                    "New solution posted",
                    "A new solution was approved for: "
                            + solution.getProblem().getTitle(),
                    NotificationType.SOLUTION,
                    solution.getId(),
                    "solution-approved:" + solution.getId()
                            + ":revision:" + revision.getRevisionNumber()
            );

            // The followers broadcast above deliberately excludes the author,
            // who needs to hear something different: not that a solution
            // appeared, but that theirs got through.
            eventPublisher.publishEvent(NotificationEvent.to(
                    solution.getAuthorId(),
                    "Your solution was approved",
                    "Your solution to \"" + solution.getProblem().getTitle()
                            + "\" is now published.",
                    NotificationType.SOLUTION,
                    solution.getId(),
                    "solution:" + solution.getId() + ":revision:"
                            + revision.getRevisionNumber() + ":approved"
            ));

            // The person who asked the question hears about it here rather
            // than at submission: until this moment the solution is pending
            // moderation and they cannot read it, so telling them earlier
            // would point at something invisible. Skipped when they answered
            // their own problem, and harmless if they also follow it — the
            // event key is per recipient.
            eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                    List.of(solution.getProblem().getAuthorId()),
                    solution.getAuthorId(),
                    "New solution to your problem",
                    "A solution to \"" + solution.getProblem().getTitle()
                            + "\" was published.",
                    NotificationType.SOLUTION,
                    solution.getId(),
                    "solution:" + solution.getId() + ":problem-author"
            ));
        } else if (request.reviewStatus() == ReviewStatus.REJECTED) {
            // The reason is the whole point of this one — an author told only
            // "rejected" cannot fix anything.
            eventPublisher.publishEvent(NotificationEvent.to(
                    solution.getAuthorId(),
                    "Your solution needs changes",
                    "Your solution to \"" + solution.getProblem().getTitle()
                            + "\" was not approved: "
                            + revision.getRejectionReason(),
                    NotificationType.SOLUTION,
                    solution.getId(),
                    "solution:" + solution.getId() + ":revision:"
                            + revision.getRevisionNumber() + ":rejected"
            ));
        }
        return toResponse(solution, revision, true);
    }

    @Override
    @Transactional
    public SolutionResponse uploadAttachment(
            UUID solutionId,
            MultipartFile file,
            long expectedVersion
    ) {
        Solution solution = solutionRepository
                .findActiveByIdForUpdate(solutionId)
                .orElseThrow(() -> notFound("Solution", solutionId));
        requireAuthor(solution, requireCurrentUserId());
        requireVersion(solution, expectedVersion);
        SolutionRevision revision = editableAttachmentRevision(solution);
        if (attachmentRepository.countByRevision_Id(revision.getId()) >= MAX_ATTACHMENTS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A solution revision can contain at most 10 attachments"
            );
        }

        AttachmentValidator.ValidatedAttachment validated = attachmentValidator.validate(file);
        String storageKey = "solutions/" + solution.getId()
                + "/revisions/" + revision.getRevisionNumber()
                + "/" + UUID.randomUUID() + "." + validated.extension();
        objectStorageService.store(
                storageKey,
                new ByteArrayInputStream(validated.content()),
                validated.sizeBytes(),
                validated.mimeType()
        );
        try {
            SolutionAttachment attachment = attachmentRepository.saveAndFlush(
                    SolutionAttachment.builder()
                            .revision(revision)
                            .fileName(validated.originalFileName())
                            .storageKey(storageKey)
                            .mimeType(validated.mimeType())
                            .fileSize(validated.sizeBytes())
                            .build()
            );
            revision.getAttachments().add(attachment);
            solution.setUpdatedAt(LocalDateTime.now());
            Solution saved = solutionRepository.saveAndFlush(solution);
            return toResponse(saved, revision, true);
        } catch (RuntimeException exception) {
            deleteStoredObjectQuietly(storageKey);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void removeAttachment(
            UUID solutionId,
            UUID attachmentId,
            long expectedVersion
    ) {
        Solution solution = solutionRepository
                .findActiveByIdForUpdate(solutionId)
                .orElseThrow(() -> notFound("Solution", solutionId));
        requireAuthor(solution, requireCurrentUserId());
        requireVersion(solution, expectedVersion);
        SolutionAttachment requested = attachmentRepository
                .findByIdAndRevision_Solution_Id(attachmentId, solutionId)
                .orElseThrow(() -> notFound("Solution attachment", attachmentId));
        SolutionRevision editable = editableAttachmentRevision(solution);
        SolutionAttachment attachment = requested.getRevision().getId()
                .equals(editable.getId())
                ? requested
                : editable.getAttachments().stream()
                .filter(item -> item.getStorageKey().equals(
                        requested.getStorageKey()
                ))
                .findFirst()
                .orElseThrow(() -> notFound(
                        "Solution attachment",
                        attachmentId
                ));
        String storageKey = attachment.getStorageKey();
        editable.getAttachments().remove(attachment);
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        solution.setUpdatedAt(LocalDateTime.now());
        solutionRepository.saveAndFlush(solution);
        if (attachmentRepository.countByStorageKey(storageKey) == 0) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteStoredObjectQuietly(storageKey);
                        }
                    }
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(UUID solutionId, UUID attachmentId) {
        Solution solution = findActiveSolution(solutionId);
        SolutionAttachment attachment = attachmentRepository
                .findByIdAndRevision_Solution_Id(attachmentId, solutionId)
                .orElseThrow(() -> notFound("Solution attachment", attachmentId));
        boolean published = solution.getCurrentPublishedRevision() != null
                && solution.getCurrentPublishedRevision().getId()
                .equals(attachment.getRevision().getId());
        boolean privileged = AuthUtils.hasRole("ADMIN") || optionalCurrentUserId()
                .map(solution.getAuthorId()::equals)
                .orElse(false);
        if (!published && !privileged) {
            throw notFound("Solution attachment", attachmentId);
        }
        return objectStorageService.createDownloadUrl(
                attachment.getStorageKey(),
                DOWNLOAD_LINK_VALIDITY
        );
    }

    private SolutionRevision createRevision(
            Solution solution,
            int revisionNumber,
            SolutionRequest request
    ) {
        SolutionRevision revision = SolutionRevision.builder()
                .solution(solution)
                .revisionNumber(revisionNumber)
                .summary(normalizeRequired(request.summary(), "Summary"))
                .bodyMarkdown(sanitizeMarkdown(request.bodyMarkdown()))
                .approachType(request.approachType())
                .verificationSteps(toVerificationSteps(request.verificationSteps()))
                .testedWith(toTestedWith(request.testedWith()))
                .tradeoffs(normalizeOptional(request.tradeoffs()))
                .moderationStatus(ReviewStatus.PENDING)
                .build();
        revision.setResources(toResources(revision, request.resources()));
        return revision;
    }

    private SolutionRevision editableAttachmentRevision(Solution solution) {
        SolutionRevision latest = requireLatestRevision(solution);
        if (latest.getModerationStatus() == ReviewStatus.PENDING) {
            return latest;
        }
        SolutionUpdateRequest unchanged = new SolutionUpdateRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        SolutionRevision pending = revisionRepository.saveAndFlush(
                createRevision(
                        solution,
                        latest.getRevisionNumber() + 1,
                        unchanged,
                        latest
                )
        );
        solution.setLatestRevision(pending);
        solutionRepository.saveAndFlush(solution);
        return pending;
    }

    private SolutionRevision createRevision(
            Solution solution,
            int revisionNumber,
            SolutionUpdateRequest request,
            SolutionRevision previous
    ) {
        SolutionRevision revision = SolutionRevision.builder()
                .solution(solution)
                .revisionNumber(revisionNumber)
                .summary(request.summary() == null
                        ? previous.getSummary()
                        : normalizeRequired(request.summary(), "Summary"))
                .bodyMarkdown(request.bodyMarkdown() == null
                        ? previous.getBodyMarkdown()
                        : sanitizeMarkdown(request.bodyMarkdown()))
                .approachType(request.approachType() == null
                        ? previous.getApproachType()
                        : request.approachType())
                .verificationSteps(request.verificationSteps() == null
                        ? copyVerificationSteps(previous.getVerificationSteps())
                        : toVerificationSteps(request.verificationSteps()))
                .testedWith(request.testedWith() == null
                        ? copyTestedWith(previous.getTestedWith())
                        : toTestedWith(request.testedWith()))
                .tradeoffs(request.tradeoffs() == null
                        ? previous.getTradeoffs()
                        : normalizeOptional(request.tradeoffs()))
                .moderationStatus(ReviewStatus.PENDING)
                .build();
        if (request.resources() == null) {
            revision.setResources(copyResources(revision, previous.getResources()));
        } else {
            revision.setResources(toResources(revision, request.resources()));
        }
        revision.setAttachments(copyAttachments(
                revision,
                previous.getAttachments()
        ));
        return revision;
    }

    private List<SolutionVerificationStep> toVerificationSteps(
            List<VerificationStepRequest> requests
    ) {
        if (requests == null) {
            return new ArrayList<>();
        }
        return requests.stream()
                .map(item -> new SolutionVerificationStep(
                        normalizeRequired(item.instruction(), "Verification instruction"),
                        normalizeRequired(item.expectedResult(), "Expected result")
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SolutionTestedWith> toTestedWith(List<TestedWithRequest> requests) {
        if (requests == null) {
            return new ArrayList<>();
        }
        return requests.stream()
                .map(item -> new SolutionTestedWith(
                        normalizeRequired(item.technology(), "Tested technology"),
                        normalizeOptional(item.version())
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SolutionResource> toResources(
            SolutionRevision revision,
            List<SolutionResourceRequest> requests
    ) {
        if (requests == null) {
            return new ArrayList<>();
        }
        List<SolutionResource> resources = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            SolutionResourceRequest item = requests.get(index);
            resources.add(SolutionResource.builder()
                    .revision(revision)
                    .type(item.type())
                    .label(normalizeRequired(item.label(), "Resource label"))
                    .url(requireHttpsUrl(item.url()))
                    .displayOrder(index)
                    .build());
        }
        return resources;
    }

    private List<SolutionVerificationStep> copyVerificationSteps(
            List<SolutionVerificationStep> source
    ) {
        return source.stream()
                .map(item -> new SolutionVerificationStep(
                        item.getInstruction(),
                        item.getExpectedResult()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SolutionTestedWith> copyTestedWith(List<SolutionTestedWith> source) {
        return source.stream()
                .map(item -> new SolutionTestedWith(
                        item.getTechnology(),
                        item.getVersion()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SolutionResource> copyResources(
            SolutionRevision revision,
            List<SolutionResource> source
    ) {
        return source.stream()
                .map(item -> SolutionResource.builder()
                        .revision(revision)
                        .type(item.getType())
                        .label(item.getLabel())
                        .url(item.getUrl())
                        .displayOrder(item.getDisplayOrder())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<SolutionAttachment> copyAttachments(
            SolutionRevision revision,
            List<SolutionAttachment> source
    ) {
        return source.stream()
                .map(item -> SolutionAttachment.builder()
                        .revision(revision)
                        .fileName(item.getFileName())
                        .storageKey(item.getStorageKey())
                        .mimeType(item.getMimeType())
                        .fileSize(item.getFileSize())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new
                ));
    }

    /**
     * Maps a page in one go. The counters, author profiles and viewer votes are
     * read once for the whole page rather than once per solution.
     */
    private Page<SolutionResponse> toResponses(
            Page<Solution> solutions,
            Function<Solution, SolutionRevision> revision,
            boolean includeModeration
    ) {
        Map<UUID, SolutionResponseEnricher.SolutionMetrics> metrics =
                solutionResponseEnricher.readAll(solutions.getContent());
        return solutions.map(solution -> toResponse(
                solution,
                revision.apply(solution),
                includeModeration,
                metrics.get(solution.getId())
        ));
    }

    /** Single-solution reads go through the same batch loader, with one row. */
    private SolutionResponse toResponse(
            Solution solution,
            SolutionRevision revision,
            boolean includeModeration
    ) {
        return toResponse(
                solution,
                revision,
                includeModeration,
                solutionResponseEnricher.readAll(List.of(solution))
                        .get(solution.getId())
        );
    }

    private SolutionResponse toResponse(
            Solution solution,
            SolutionRevision revision,
            boolean includeModeration,
            SolutionResponseEnricher.SolutionMetrics metrics
    ) {
        if (revision == null) {
            throw new IllegalStateException(
                    "Solution " + solution.getId() + " has no content revision"
            );
        }
        UserProfile author = metrics == null ? null : metrics.author();
        if (author == null) {
            throw notFound("Solution author profile", solution.getAuthorId());
        }
        String viewerVote = metrics.viewerVote();
        return new SolutionResponse(
                solution.getId(),
                solution.getProblem().getId(),
                new SolutionResponse.AuthorSummary(
                        author.getId(),
                        author.getFullName(),
                        author.getAvatarUrl()
                ),
                revision.getSummary(),
                revision.getBodyMarkdown(),
                revision.getApproachType(),
                revision.getVerificationSteps().stream()
                        .map(item -> new SolutionResponse.VerificationStep(
                                item.getInstruction(),
                                item.getExpectedResult()
                        ))
                        .toList(),
                revision.getTestedWith().stream()
                        .map(item -> new SolutionResponse.TestedWith(
                                item.getTechnology(),
                                item.getVersion()
                        ))
                        .toList(),
                revision.getTradeoffs(),
                revision.getResources().stream()
                        .map(item -> new SolutionResponse.ResourceSummary(
                                item.getId(),
                                item.getType(),
                                item.getLabel(),
                                item.getUrl(),
                                item.getDisplayOrder()
                        ))
                        .toList(),
                revision.getAttachments().stream()
                        .map(item -> new SolutionResponse.AttachmentSummary(
                                item.getId(),
                                item.getFileName(),
                                item.getMimeType(),
                                item.getFileSize(),
                                "/api/v1/solutions/" + solution.getId()
                                        + "/attachments/" + item.getId()
                                        + "/download",
                                item.getCreatedAt()
                        ))
                        .toList(),
                findAcceptance(solution.getProblem(), solution.getId())
                        .isPresent(),
                metrics.voteScore(),
                metrics.commentCount(),
                viewerVote,
                solution.getVersion(),
                includeModeration
                        ? new SolutionResponse.ModerationDetails(
                        revision.getId(),
                        revision.getRevisionNumber(),
                        revision.getModerationStatus(),
                        revision.getRejectionReason(),
                        revision.getReviewedBy(),
                        revision.getReviewedAt()
                )
                        : null,
                solution.getCreatedAt(),
                solution.getUpdatedAt()
        );
    }

    private Solution findActiveSolution(UUID id) {
        return solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> notFound("Solution", id));
    }

    private SolutionRevision requireLatestRevision(Solution solution) {
        if (solution.getLatestRevision() == null) {
            throw new IllegalStateException(
                    "Solution " + solution.getId() + " has no latest revision"
            );
        }
        return solution.getLatestRevision();
    }

    private UserProfile requireAuthorProfile(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> notFound("Solution author profile", id));
    }

    private void requireAuthor(Solution solution, UUID userId) {
        if (!solution.getAuthorId().equals(userId)) {
            throw forbidden("You are not the author of this solution");
        }
    }

    private void requireAcceptancePermission(Problem problem) {
        UUID userId = requireCurrentUserId();
        if (!AuthUtils.hasRole("ADMIN") && !problem.getAuthorId().equals(userId)) {
            throw forbidden(
                    "Only the problem author or an administrator can manage acceptance"
            );
        }
        if (problem.getStatus() != ProblemStatus.PUBLISHED
                && problem.getStatus() != ProblemStatus.RESOLVED) {
            throw conflict("Acceptance can only be changed on a published problem");
        }
    }

    private void requireVersion(Solution solution, long expectedVersion) {
        if (solution.getVersion() != expectedVersion) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "The solution changed after it was read; fetch it again before editing"
            );
        }
    }

    private Optional<ProblemAcceptedSolution> findAcceptance(
            Problem problem,
            UUID solutionId
    ) {
        return problem.getAcceptedSolutions().stream()
                .filter(item -> item.getSolutionId().equals(solutionId))
                .findFirst();
    }

    private void reopenIfNoAcceptedSolutions(Problem problem) {
        if (problem.getAcceptedSolutions().isEmpty()
                && problem.getStatus() == ProblemStatus.RESOLVED) {
            problem.setStatus(ProblemStatus.PUBLISHED);
        }
    }

    private void validateUpdateRequest(SolutionUpdateRequest request) {
        if (request.summary() == null
                && request.bodyMarkdown() == null
                && request.approachType() == null
                && request.verificationSteps() == null
                && request.testedWith() == null
                && request.tradeoffs() == null
                && request.resources() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one solution field must be provided"
            );
        }
    }

    private void validateReviewRequest(UpdateSolutionReviewStatusRequest request) {
        if (request.reviewStatus() != ReviewStatus.APPROVED
                && request.reviewStatus() != ReviewStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review status must be APPROVED or REJECTED"
            );
        }
        boolean hasReason = request.rejectionReason() != null
                && !request.rejectionReason().isBlank();
        if (request.reviewStatus() == ReviewStatus.REJECTED && !hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required when rejecting a solution"
            );
        }
        if (request.reviewStatus() == ReviewStatus.APPROVED && hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is only allowed for rejected solutions"
            );
        }
    }

    private String sanitizeMarkdown(String markdown) {
        String value = normalizeRequired(markdown, "Solution body");
        value = DANGEROUS_MARKUP.matcher(value).replaceAll("");
        value = EVENT_HANDLER.matcher(value).replaceAll("");
        value = JAVASCRIPT_SCHEME.matcher(value).replaceAll("");
        return value;
    }

    private String requireHttpsUrl(String value) {
        String normalized = normalizeRequired(value, "Resource URL");
        try {
            URI uri = URI.create(normalized);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resource URL must be a valid HTTPS URL"
            );
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " cannot be blank"
            );
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw forbidden("Only ADMIN can review solutions");
        }
    }

    private UUID requireCurrentUserId() {
        return optionalCurrentUserId().orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid Keycloak access token is required"
                )
        );
    }

    private Optional<UUID> optionalCurrentUserId() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private void validatePagination(int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be non-negative and page size must be between 1 and 100"
            );
        }
    }

    private void deleteStoredObjectQuietly(String storageKey) {
        try {
            objectStorageService.delete(storageKey);
        } catch (RuntimeException exception) {
            log.error("Unable to delete object storage key {}", storageKey, exception);
        }
    }

    private ResourceNotFoundException notFound(String resource, UUID id) {
        return new ResourceNotFoundException(resource + " not found with id: " + id);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
