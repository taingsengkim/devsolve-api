package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import kh.edu.istad.ite.devsoleapi.common.listing.ViewCountGuard;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.ProfanityFlagger;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemTechnologyRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.RelatedProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagId;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagResolver;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_TECHNOLOGIES = 20;
    private static final Duration DOWNLOAD_LINK_VALIDITY =
            Duration.ofMinutes(5);
    private static final Set<ProblemStatus> PUBLIC_STATUSES = EnumSet.of(
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED,
            ProblemStatus.CLOSED
    );
    /**
     * Statuses whose edits never reach a reader, so they are held to the
     * lighter draft rules.
     */
    private static final Set<ProblemStatus> AUTHOR_EDITABLE_STATUSES =
            EnumSet.of(ProblemStatus.DRAFT, ProblemStatus.REJECTED);
    /**
     * Published work an author may still correct. Edits here apply in place
     * and stay visible: a typo fix should not pull a problem other people are
     * reading, and re-reviewing every edit would make moderation the
     * bottleneck on its own content. Abusive edits are caught the way the
     * rest of the platform catches them - reactively, through flags.
     */
    private static final Set<ProblemStatus> LIVE_EDITABLE_STATUSES =
            EnumSet.of(ProblemStatus.PUBLISHED, ProblemStatus.RESOLVED);
    private static final Set<ProblemStatus> EDITABLE_STATUSES = EnumSet.of(
            ProblemStatus.DRAFT,
            ProblemStatus.REJECTED,
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED
    );
    /**
     * Below four characters a needle is one or two trigrams and resembles
     * most of the corpus; past two hundred it is a paragraph whose similarity
     * to any single title rounds to zero. Both ends answer with no
     * suggestions rather than with nonsense ones.
     */
    private static final int MIN_RELATED_QUERY_LENGTH = 4;
    private static final int MAX_RELATED_QUERY_LENGTH = 200;
    private static final int MAX_RELATED_RESULTS = 20;
    private static final Set<String> PROBLEM_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "publishedAt",
            "title",
            "viewCount",
            "status"
    );

    private final ProblemRepository problemRepository;
    private final ProblemTechnologyRepository technologyRepository;
    private final ProblemAttachmentRepository attachmentRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProblemMapper problemMapper;
    private final ProblemContentSafety contentSafety;
    private final AttachmentValidator attachmentValidator;
    private final ObjectStorageService objectStorageService;
    private final FollowNotificationService followNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final TagResolver tagResolver;
    private final SolutionRepository solutionRepository;
    private final ViewCountGuard viewCountGuard;
    private final ProfanityFlagger profanityFlagger;

    @Autowired(required = false)
    private ProblemResponseEnricher responseEnricher;

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findPublished(
            UUID categoryId,
            SdlcPhase sdlcPhase,
            String tag,
            String technology,
            String query,
            ProblemStatus status,
            boolean unansweredOnly,
            ListingSort sort,
            Pageable pageable
    ) {
        String tagSlug = tag == null ? null : normalizeSlug(tag);
        String technologyName = trimToNull(technology);
        if (technologyName != null) {
            technologyName = technologyName.toLowerCase(Locale.ROOT);
        }
        String queryPattern = containsPattern(query);
        ProblemStatus publicStatus = requirePublicStatus(status);
        ListingSort effectiveSort = sort == null ? ListingSort.NEWEST : sort;

        if (effectiveSort.isScoreOrdered()) {
            // The ordering is fixed by the query, so the caller's sort is
            // dropped rather than silently fighting the ORDER BY.
            LocalDateTime window = effectiveSort.windowStart();
            return toResponses(problemRepository.findPublishedByScore(
                    categoryId,
                    sdlcPhase,
                    tagSlug,
                    technologyName,
                    queryPattern,
                    publicStatus,
                    unansweredOnly,
                    window == null
                            ? null
                            : window.atZone(ZoneOffset.UTC).toInstant(),
                    VoteType.PROBLEM,
                    PageRequest.of(
                            pageable.getPageNumber(),
                            pageable.getPageSize()
                    )
            ));
        }

        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        return toResponses(problemRepository.findPublished(
                categoryId,
                sdlcPhase,
                tagSlug,
                technologyName,
                queryPattern,
                publicStatus,
                unansweredOnly,
                stabilize(validatedPageable)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelatedProblemResponse> findRelated(
            String query,
            UUID excludeId,
            int limit
    ) {
        String needle = relatedNeedle(query);
        if (needle == null) {
            return List.of();
        }
        return problemRepository.findRelated(
                needle,
                excludeId,
                Math.clamp(limit, 1, MAX_RELATED_RESULTS)
        ).stream().map(this::toRelatedResponse).toList();
    }

    /**
     * Normalises the draft text into something the trigram operators can rank
     * with, or null when there is nothing worth ranking. Lowercasing happens
     * here because the query lowercases the column to hit the indexed
     * expression, and one side matching another's case would find nothing.
     */
    private String relatedNeedle(String query) {
        String normalized = trimToNull(query);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() < MIN_RELATED_QUERY_LENGTH) {
            return null;
        }
        return normalized.length() <= MAX_RELATED_QUERY_LENGTH
                ? normalized
                : normalized.substring(0, MAX_RELATED_QUERY_LENGTH);
    }

    private RelatedProblemResponse toRelatedResponse(
            RelatedProblemProjection row
    ) {
        ProblemStatus status = ProblemStatus.valueOf(row.getStatus());
        return new RelatedProblemResponse(
                row.getId(),
                row.getTitle(),
                status,
                status == ProblemStatus.RESOLVED,
                row.getSolutionCount(),
                row.getViewCount()
        );
    }

    /**
     * Appends the id to whatever the caller asked to sort by. Timestamps are
     * not unique, and an offset page boundary landing between two problems
     * that share one shows the same problem twice while dropping another.
     */
    private Pageable stabilize(Pageable pageable) {
        if (pageable.getSort().getOrderFor("id") != null) {
            return pageable;
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"))
        );
    }

    private String containsPattern(String query) {
        String normalized = trimToNull(query);
        return normalized == null
                ? null
                : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    /**
     * A caller may narrow the feed to one public status but not use the filter
     * to reach into drafts or rejected problems, which is what a bare
     * pass-through of this parameter would allow.
     */
    private ProblemStatus requirePublicStatus(ProblemStatus status) {
        if (status == null) {
            return null;
        }
        if (!PUBLIC_STATUSES.contains(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "status must be PUBLISHED, RESOLVED, or CLOSED"
            );
        }
        return status;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findMine(Pageable pageable) {
        UUID authorId = currentUserId();
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        return toResponses(problemRepository.findAllByAuthorId(
                authorId,
                validatedPageable
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findPublishedByAuthor(
            UUID authorId,
            Pageable pageable
    ) {
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        return toResponses(problemRepository.findAllByAuthorIdAndStatusIn(
                authorId,
                PUBLIC_STATUSES,
                validatedPageable
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findForModeration(
            ProblemStatus status,
            Pageable pageable
    ) {
        requireAdmin();
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        ProblemStatus effectiveStatus = status == null
                ? ProblemStatus.PENDING_APPROVAL
                : status;
        return toResponses(problemRepository.findAllByStatus(
                effectiveStatus,
                validatedPageable
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemResponse findById(UUID id) {
        Problem problem = findProblem(id);
        if (!canView(problem)) {
            throw notFound(id);
        }
        return toResponse(problem);
    }

    @Override
    @Transactional
    public ProblemResponse createDraft(CreateProblemRequest request) {
        return create(request, false);
    }

    @Override
    @Transactional
    public ProblemResponse createAndSubmit(CreateProblemRequest request) {
        return create(request, true);
    }

    @Override
    @Transactional
    public ProblemResponse update(
            UUID id,
            ProblemUpdateRequest request,
            long expectedVersion
    ) {
        Problem problem = findOwnedEditableProblem(id);
        if (problem.getVersion() != expectedVersion) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "The problem changed after it was read; fetch it again before editing"
            );
        }
        return applyUpdate(problem, request);
    }

    private ProblemResponse applyUpdate(
            Problem problem,
            ProblemUpdateRequest request
    ) {

        if (request.categoryId() != null) {
            validateDraftCategory(request.categoryId());
            problem.setCategoryId(request.categoryId());
        }
        if (request.title() != null) {
            problem.setTitle(contentSafety.normalizeText(request.title()));
        }
        if (request.problemType() != null) {
            problem.setProblemType(request.problemType());
        }
        if (request.description() != null) {
            problem.setDescription(
                    Objects.requireNonNullElse(
                            contentSafety.normalizeText(request.description()),
                            ""
                    )
            );
        }
        if (request.sdlcPhase() != null) {
            problem.setSdlcPhase(request.sdlcPhase());
        }
        if (request.expectedBehavior() != null) {
            problem.setExpectedBehavior(normalizeOptional(request.expectedBehavior()));
        }
        if (request.actualBehavior() != null) {
            problem.setActualBehavior(normalizeOptional(request.actualBehavior()));
        }
        if (request.reproductionSteps() != null) {
            problem.setReproductionSteps(normalizeSteps(request.reproductionSteps()));
        }
        if (request.environment() != null) {
            problem.setEnvironment(request.environment().stream()
                    .map(item -> new ProblemEnvironment(
                            requireText(item.technology(), "Environment technology"),
                            trimToNull(item.version())
                    ))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        if (request.attemptsTried() != null) {
            problem.setAttemptsTried(normalizeOptional(request.attemptsTried()));
        }
        if (request.errorMessage() != null) {
            problem.setErrorMessage(normalizeOptional(request.errorMessage()));
        }
        if (request.severity() != null) {
            problem.setSeverity(request.severity());
        }
        if (request.repositoryUrl() != null) {
            problem.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        }

        // An edit to live content has to clear the same bar the moderator
        // approved it against, or a published problem could be emptied out
        // one PATCH at a time.
        if (LIVE_EDITABLE_STATUSES.contains(problem.getStatus())) {
            validateForPublication(problem);
        }

        Problem saved = problemRepository.saveAndFlush(problem);
        if (request.technologies() != null) {
            replaceTechnologies(saved, request.technologies());
        }
        if (request.tagIds() != null || request.newTagNames() != null) {
            replaceTags(saved, request.tagIds(), request.newTagNames());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProblemResponse submit(UUID id) {
        Problem problem = findOwnedEditableProblem(id);
        validateForPublication(problem);
        problem.setStatus(ProblemStatus.PENDING_APPROVAL);
        problem.setPublishedAt(null);
        Problem saved = problemRepository.saveAndFlush(problem);
        reviewLanguage(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProblemResponse moderate(
            UUID id,
            ProblemModerationRequest request
    ) {
        requireAdmin();
        if (request.status() != ProblemStatus.PUBLISHED
                && request.status() != ProblemStatus.REJECTED) {
            throw badRequest(
                    "Moderation status must be PUBLISHED or REJECTED"
            );
        }

        Problem problem = findProblem(id);
        if (problem.getStatus() != ProblemStatus.PENDING_APPROVAL) {
            throw conflict(
                    "Only a problem pending approval can be moderated"
            );
        }

        if (request.status() == ProblemStatus.PUBLISHED) {
            validateForPublication(problem);
            problem.setPublishedAt(Instant.now());
        } else {
            problem.setPublishedAt(null);
        }
        problem.setStatus(request.status());
        Problem saved = problemRepository.saveAndFlush(problem);
        if (saved.getStatus() == ProblemStatus.PUBLISHED) {
            followNotificationService.notifyFollowers(
                    FollowType.USER,
                    saved.getAuthorId(),
                    saved.getAuthorId(),
                    "New problem published",
                    saved.getTitle(),
                    NotificationType.PROBLEM,
                    saved.getId(),
                    "problem-published:" + saved.getId()
            );
        }

        // The broadcast above reaches the author's followers, not the author.
        // They are the one waiting on the decision, and a rejection they are
        // never told about looks identical to a problem still in the queue.
        //
        // Keyed on updatedAt, which the flush above has just set: a rejected
        // problem can be corrected and resubmitted, and each verdict is its
        // own news. There is no revision counter on a problem to key on
        // instead.
        eventPublisher.publishEvent(NotificationEvent.to(
                saved.getAuthorId(),
                saved.getStatus() == ProblemStatus.PUBLISHED
                        ? "Your problem was published"
                        : "Your problem needs changes",
                saved.getStatus() == ProblemStatus.PUBLISHED
                        ? "\"" + saved.getTitle() + "\" is now live."
                        : "\"" + saved.getTitle()
                                + "\" was not approved. Edit it and submit it "
                                + "again.",
                NotificationType.PROBLEM,
                saved.getId(),
                "problem:" + saved.getId() + ":moderated:"
                        + saved.getUpdatedAt()
        ));

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Problem problem = problemRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> notFound(id));
        UUID userId = currentUserId();
        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        boolean author = problem.getAuthorId().equals(userId);

        if (!admin && !author) {
            throw forbidden("You are not allowed to delete this problem");
        }

        // A deleted parent must not leave independently addressable solutions
        // behind. Keep their rows for audit/history, but hide them in the same
        // transaction as the problem.
        solutionRepository.softDeleteAllByProblemId(
                problem.getId(),
                LocalDateTime.now()
        );

        Set<UUID> associatedTagIds = problemTagRepository
                .findAllByProblemId(problem.getId())
                .stream()
                .map(problemTag -> problemTag.getTag().getId())
                .collect(Collectors.toSet());
        if (!associatedTagIds.isEmpty()) {
            tagRepository.decrementUsageCounts(associatedTagIds);
        }
        discardAttachments(problem.getId());
        problem.softDelete();
        problemRepository.saveAndFlush(problem);
    }

    /**
     * A deleted problem cannot be restored, so its uploads are unreachable
     * from that point on. Dropping the rows and the stored objects keeps the
     * bucket from filling with files nothing can ever link to again.
     */
    private void discardAttachments(UUID problemId) {
        List<ProblemAttachment> attachments = attachmentRepository
                .findAllByProblemIdOrderByCreatedAtAsc(problemId);
        if (attachments.isEmpty()) {
            return;
        }
        List<String> storageKeys = attachments.stream()
                .map(ProblemAttachment::getStorageKey)
                .filter(Objects::nonNull)
                .toList();
        attachmentRepository.deleteAll(attachments);
        attachmentRepository.flush();
        deleteStoredObjectsAfterCommit(storageKeys);
    }

    @Override
    @Transactional
    public ProblemResponse uploadAttachment(
            UUID id,
            MultipartFile file
    ) {
        Problem problem = findOwnedEditableProblem(id);
        AttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(file);
        String storageKey = "problems/"
                + problem.getId()
                + "/"
                + UUID.randomUUID()
                + "."
                + validated.extension();

        objectStorageService.store(
                storageKey,
                new ByteArrayInputStream(validated.content()),
                validated.sizeBytes(),
                validated.mimeType()
        );
        // The object is already in the bucket while the row that points at it
        // is not committed yet. Anything that rolls the transaction back after
        // this point - here or further up the call stack - would strand the
        // file, so the compensation is hung off the transaction itself rather
        // than off this block.
        deleteStoredObjectIfRolledBack(storageKey);

        try {
            ProblemAttachment attachment = ProblemAttachment.builder()
                    .problem(problem)
                    .originalFileName(validated.originalFileName())
                    .storageKey(storageKey)
                    .mimeType(validated.mimeType())
                    .sizeBytes(validated.sizeBytes())
                    .uploadedBy(currentUserId())
                    .build();
            attachmentRepository.saveAndFlush(attachment);
            return toResponse(problem);
        } catch (RuntimeException exception) {
            deleteStoredObjectQuietly(storageKey);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void removeAttachment(
            UUID problemId,
            UUID attachmentId
    ) {
        Problem problem = findProblem(problemId);
        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        if (!admin) {
            requireOwnedEditable(problem);
        }

        ProblemAttachment attachment = attachmentRepository
                .findByIdAndProblemId(attachmentId, problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem attachment not found"
                ));
        String storageKey = attachment.getStorageKey();
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();
        deleteStoredObjectsAfterCommit(List.of(storageKey));
    }

    @Override
    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(
            UUID problemId,
            UUID attachmentId
    ) {
        Problem problem = findProblem(problemId);
        if (!canView(problem)) {
            throw notFound(problemId);
        }
        ProblemAttachment attachment = attachmentRepository
                .findByIdAndProblemId(attachmentId, problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem attachment not found"
                ));
        return objectStorageService.createDownloadUrl(
                attachment.getStorageKey(),
                DOWNLOAD_LINK_VALIDITY
        );
    }

    @Override
    @Transactional
    public void incrementViewCount(UUID id) {
        // Counted once per viewer per window. The endpoint still succeeds on
        // a repeat view — the caller is reporting a page load, not asking for
        // a number, and failing it would only teach clients to retry.
        if (!viewCountGuard.shouldCount("problem", id)) {
            return;
        }
        if (problemRepository.incrementPublicViewCount(id) == 0) {
            throw notFound(id);
        }
    }

    private ProblemResponse create(
            CreateProblemRequest request,
            boolean submit
    ) {
        UUID authorId = currentUserId();
        findAuthor(authorId);
        validateDraftCategory(request.categoryId());

        String title = contentSafety.normalizeText(request.title());
        String description = Objects.requireNonNullElse(
                contentSafety.normalizeText(request.description()),
                ""
        );

        Problem problem = problemMapper.toEntity(request, authorId);
        problem.setTitle(title);
        problem.setDescription(description);
        problem.setExpectedBehavior(normalizeOptional(request.expectedBehavior()));
        problem.setActualBehavior(normalizeOptional(request.actualBehavior()));
        problem.setReproductionSteps(normalizeSteps(request.reproductionSteps()));
        problem.setEnvironment(request.environment() == null
                ? new ArrayList<>()
                : request.environment().stream()
                .map(item -> new ProblemEnvironment(
                        requireText(item.technology(), "Environment technology"),
                        trimToNull(item.version())
                ))
                .collect(Collectors.toCollection(ArrayList::new)));
        problem.setAttemptsTried(normalizeOptional(request.attemptsTried()));
        problem.setErrorMessage(normalizeOptional(request.errorMessage()));
        problem.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        problem.setStatus(ProblemStatus.DRAFT);
        problem.setViewCount(0L);
        problem = problemRepository.saveAndFlush(problem);
        replaceTechnologies(problem, request.technologies());
        replaceTags(problem, request.tagIds(), request.newTagNames());

        if (submit) {
            validateForPublication(problem);
            problem.setStatus(ProblemStatus.PENDING_APPROVAL);
            problem = problemRepository.saveAndFlush(problem);
            reviewLanguage(problem);
        }
        return toResponse(problem);
    }

    /**
     * Puts the problem's prose in front of a moderator if it swears.
     *
     * <p>On submission rather than on every save. A draft is private to its
     * author, so flagging one asks a moderator to rule on writing nobody
     * else can read and the author may not have finished. Slurs are refused
     * earlier than this, by the normalising the fields already go through,
     * so nothing here can reach a draft either.
     *
     * <p>The error message field is left out on purpose. It holds a pasted
     * stack trace or log line — output, not something the author wrote — and
     * scanning it flags people for what their tooling printed.
     */
    private void reviewLanguage(Problem problem) {
        profanityFlagger.review(
                FlaggableType.PROBLEM,
                problem.getId(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getExpectedBehavior(),
                problem.getActualBehavior(),
                problem.getAttemptsTried()
        );
    }

    private void validateForPublication(Problem problem) {
        if (problem.getTitle() == null
                || problem.getTitle().length() < 10
                || problem.getTitle().length() > 180) {
            throw badRequest(
                    "A submitted problem needs a title between 10 and "
                            + "180 characters"
            );
        }
        String description = trimToNull(problem.getDescription());
        if (description == null || description.length() < 30) {
            throw badRequest(
                    "A submitted problem needs a description of at least "
                            + "30 characters"
            );
        }
        if (description.length() > 20_000) {
            throw badRequest(
                    "Problem description cannot exceed 20,000 characters"
            );
        }
        if (problem.getCategoryId() == null) {
            throw badRequest("A submitted problem needs a category");
        }
        categoryRepository.findByIdAndScopeAndIsActiveTrue(
                problem.getCategoryId(),
                CategoryScope.PROBLEM
        ).orElseThrow(() -> badRequest(
                "A submitted problem needs an active PROBLEM category"
        ));
        if (problem.getProblemType() == null) {
            throw badRequest("A submitted problem needs a problem type");
        }
        if (problem.getProblemType() == ProblemType.BUG) {
            if (trimToNull(problem.getExpectedBehavior()) == null
                    || trimToNull(problem.getActualBehavior()) == null
                    || problem.getReproductionSteps().isEmpty()) {
                throw badRequest(
                        "BUG problems require expectedBehavior, actualBehavior, "
                                + "and at least one reproduction step"
                );
            }
        }
    }

    private void validateDraftCategory(UUID categoryId) {
        if (categoryId == null) {
            throw badRequest("Category is required");
        }
        categoryRepository
                .findByIdAndScopeAndIsActiveTrue(
                        categoryId,
                        CategoryScope.PROBLEM
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active problem category not found"
                ));
    }

    private void replaceTechnologies(
            Problem problem,
            List<ProblemTechnologyRequest> requests
    ) {
        List<ProblemTechnologyRequest> safeRequests = requests == null
                ? List.of()
                : requests;
        if (safeRequests.size() > MAX_TECHNOLOGIES) {
            throw badRequest(
                    "A problem can contain at most 20 technologies"
            );
        }

        Set<String> uniqueness = new HashSet<>();
        List<ProblemTechnology> technologies = new ArrayList<>();
        for (ProblemTechnologyRequest request : safeRequests) {
            if (request == null) {
                throw badRequest("Technology entries cannot be null");
            }
            String name = trimToNull(request.name());
            String version = trimToNull(request.version());
            if (name == null) {
                throw badRequest("Technology name cannot be blank");
            }
            String key = name.toLowerCase(Locale.ROOT)
                    + "\u0000"
                    + Objects.requireNonNullElse(version, "")
                    .toLowerCase(Locale.ROOT);
            if (!uniqueness.add(key)) {
                throw badRequest(
                        "Duplicate technology name and version"
                );
            }
            technologies.add(ProblemTechnology.builder()
                    .problem(problem)
                    .name(name)
                    .version(version)
                    .build());
        }

        technologyRepository.deleteAllByProblemId(problem.getId());
        technologyRepository.flush();
        if (!technologies.isEmpty()) {
            technologyRepository.saveAllAndFlush(technologies);
        }
    }

    private void replaceTags(
            Problem problem,
            Set<UUID> tagIds,
            Set<String> tagNames
    ) {
        Map<UUID, Tag> desired = tagResolver.resolve(
                tagIds,
                tagNames,
                "problem"
        );

        List<ProblemTag> existing = problemTagRepository
                .findAllByProblemId(problem.getId());
        Map<UUID, ProblemTag> existingByTagId = existing.stream()
                .collect(Collectors.toMap(
                        item -> item.getTag().getId(),
                        Function.identity()
                ));

        Set<UUID> removedIds = new HashSet<>(existingByTagId.keySet());
        removedIds.removeAll(desired.keySet());
        Set<UUID> addedIds = new HashSet<>(desired.keySet());
        addedIds.removeAll(existingByTagId.keySet());

        removedIds.forEach(tagId -> problemTagRepository.deleteById(
                new ProblemTagId(problem.getId(), tagId)
        ));
        if (!removedIds.isEmpty()) {
            problemTagRepository.flush();
            tagRepository.decrementUsageCounts(removedIds);
        }

        if (!addedIds.isEmpty()) {
            List<ProblemTag> additions = addedIds.stream()
                    .map(tagId -> ProblemTag.builder()
                            .id(new ProblemTagId(problem.getId(), tagId))
                            .problem(problem)
                            .tag(desired.get(tagId))
                            .build())
                    .toList();
            problemTagRepository.saveAllAndFlush(additions);
            tagRepository.incrementUsageCounts(addedIds);
        }
    }

    private Problem findOwnedEditableProblem(UUID id) {
        Problem problem = findProblem(id);
        requireOwnedEditable(problem);
        return problem;
    }

    private void requireOwnedEditable(Problem problem) {
        if (!problem.getAuthorId().equals(currentUserId())) {
            throw forbidden("You are not the author of this problem");
        }
        if (problem.getStatus() == ProblemStatus.PENDING_APPROVAL) {
            throw conflict(
                    "A problem awaiting moderation cannot be edited; wait "
                            + "for the decision, then edit it"
            );
        }
        if (!EDITABLE_STATUSES.contains(problem.getStatus())) {
            throw conflict("A closed problem can no longer be edited");
        }
    }

    private boolean canView(Problem problem) {
        if (PUBLIC_STATUSES.contains(problem.getStatus())
                || AuthUtils.hasRole(ADMIN_ROLE)) {
            return true;
        }
        return currentUserIdIfPresent()
                .map(problem.getAuthorId()::equals)
                .orElse(false);
    }

    private Page<ProblemResponse> toResponses(Page<Problem> problems) {
        ProblemAssociations associations =
                loadAssociations(problems.getContent());
        return problems.map(problem -> toResponse(problem, associations));
    }

    private ProblemResponse toResponse(Problem problem) {
        return toResponse(problem, loadAssociations(List.of(problem)));
    }

    /**
     * Everything a page of problems needs from other tables, fetched once for
     * the whole page. Reading these per problem instead turns a single listing
     * into upwards of a hundred queries.
     */
    private ProblemAssociations loadAssociations(List<Problem> problems) {
        if (problems.isEmpty()) {
            return ProblemAssociations.empty();
        }
        List<UUID> problemIds = problems.stream()
                .map(Problem::getId)
                .toList();
        Set<UUID> authorIds = problems.stream()
                .map(Problem::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> categoryIds = problems.stream()
                .map(Problem::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new ProblemAssociations(
                userProfileRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(
                                UserProfile::getId,
                                Function.identity()
                        )),
                categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(
                                Category::getId,
                                Function.identity()
                        )),
                technologyRepository
                        .findAllByProblemIdInOrderByNameAsc(problemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                technology -> technology.getProblem().getId()
                        )),
                problemTagRepository.findAllByProblemIdIn(problemIds).stream()
                        .collect(Collectors.groupingBy(
                                problemTag -> problemTag.getProblem().getId()
                        )),
                attachmentRepository
                        .findAllByProblemIdInOrderByCreatedAtAsc(problemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                attachment -> attachment.getProblem().getId()
                        )),
                responseEnricher == null
                        ? Map.of()
                        : responseEnricher.readAll(problems)
        );
    }

    private ProblemResponse toResponse(
            Problem problem,
            ProblemAssociations associations
    ) {
        return problemMapper.toResponse(
                problem,
                // Null rather than a 404: one problem whose author profile has
                // gone missing should cost that row its byline, not take the
                // whole page down with it.
                associations.authors().get(problem.getAuthorId()),
                associations.categories().get(problem.getCategoryId()),
                associations.technologies().getOrDefault(
                        problem.getId(),
                        List.of()
                ),
                associations.tags().getOrDefault(problem.getId(), List.of()),
                associations.attachments().getOrDefault(
                        problem.getId(),
                        List.of()
                ),
                contentSafety.warnings(
                        problem.getTitle(),
                        problem.getDescription()
                ),
                associations.metrics().getOrDefault(
                        problem.getId(),
                        ProblemResponseMetrics.empty()
                )
        );
    }

    private record ProblemAssociations(
            Map<UUID, UserProfile> authors,
            Map<UUID, Category> categories,
            Map<UUID, List<ProblemTechnology>> technologies,
            Map<UUID, List<ProblemTag>> tags,
            Map<UUID, List<ProblemAttachment>> attachments,
            Map<UUID, ProblemResponseMetrics> metrics
    ) {
        static ProblemAssociations empty() {
            return new ProblemAssociations(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }
    }

    private Problem findProblem(UUID id) {
        return problemRepository.findActiveById(id)
                .orElseThrow(() -> notFound(id));
    }

    private UserProfile findAuthor(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem author profile not found"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "The Keycloak subject must be a UUID"
            );
        }
    }

    private Optional<UUID> currentUserIdIfPresent() {
        Authentication authentication = AuthUtils.getAuth();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    jwt.getToken().getSubject()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw forbidden("Only ADMIN can moderate problems");
        }
    }

    private String normalizeSlug(String value) {
        return TagResolver.normalizeSlug(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptional(String value) {
        String normalized = contentSafety.normalizeText(value);
        return trimToNull(normalized);
    }

    private List<String> normalizeSteps(List<String> steps) {
        if (steps == null) {
            return new ArrayList<>();
        }
        if (steps.size() > 20) {
            throw badRequest("A problem can contain at most 20 reproduction steps");
        }
        return steps.stream()
                .map(step -> requireText(step, "Reproduction step"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String requireText(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw badRequest(fieldName + " cannot be blank");
        }
        return normalized;
    }

    /**
     * Storage is not transactional, so the rows go first and the objects
     * follow only once the database has actually committed. A crash between
     * the two leaves an unreferenced object, which is the harmless direction
     * to fail in - the alternative is a row pointing at a file that is gone.
     */
    private void deleteStoredObjectsAfterCommit(List<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storageKeys.forEach(this::deleteStoredObjectQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        storageKeys.forEach(
                                ProblemServiceImpl.this
                                        ::deleteStoredObjectQuietly
                        );
                    }
                }
        );
    }

    private void deleteStoredObjectIfRolledBack(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteStoredObjectQuietly(storageKey);
                        }
                    }
                }
        );
    }

    private void deleteStoredObjectQuietly(String storageKey) {
        try {
            objectStorageService.delete(storageKey);
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to delete object storage key {}",
                    storageKey,
                    exception
            );
        }
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException(
                "Problem not found with id: " + id
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
