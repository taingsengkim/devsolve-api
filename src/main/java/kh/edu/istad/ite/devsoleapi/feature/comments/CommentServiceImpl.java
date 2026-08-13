package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportDiscussionAccess;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final List<ReviewStatus> PUBLIC_SOLUTION_STATUSES =
            List.of(ReviewStatus.APPROVED);

    private final CommentRepository commentRepository;
    private final ReportService reportService;
    private final ProblemRepository problemRepository;
    private final SolutionRepository solutionRepository;
    private final ProgramRepository programRepository;
    private final ShowCasesRepository showCasesRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CommentResponse create(CreateCommentRequest request) {
        TargetAccess access = requireTargetAccess(
                request.commentableType(),
                request.commentableId()
        );
        requireCanComment(access);
        validateInternalComment(request, access);

        Comment comment = new Comment();
        comment.setCommentableType(request.commentableType());
        comment.setCommentableId(request.commentableId());
        comment.setAuthorId(currentUserId());
        comment.setContent(request.content().trim());
        comment.setInternal(request.internal());

        if (request.parentCommentId() != null) {
            Comment parent = findVisibleComment(
                    request.parentCommentId(),
                    access.canViewInternal()
            );
            requireSameTarget(parent, request);
            if (parent.isInternal() != request.internal()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "A reply must use the same visibility as its parent comment"
                );
            }
            comment.setParentComment(parent);
        }

        Comment saved = commentRepository.saveAndFlush(comment);
        notifyAboutComment(saved, access);

        return toResponse(saved, access.canViewInternal());
    }

    /**
     * Tells the author of the thing being discussed, and the author of the
     * comment being replied to. Both are filtered against the commenter, so
     * replying to yourself or commenting on your own work is silent.
     *
     * <p>Internal comments never reach the target's author. On a report the
     * author is the researcher, and internal comments are the organization
     * talking among themselves about their work — a notification would leak
     * both the fact and often the substance of it. Replies are exempt: a reply
     * to an internal comment can only reach someone who wrote one, and who
     * therefore already has internal access.
     */
    private void notifyAboutComment(Comment comment, TargetAccess access) {
        UUID authorId = currentUserId();
        String target = comment.getCommentableType().name().toLowerCase();

        if (!comment.isInternal() && access.authorId() != null) {
            eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                    List.of(access.authorId()),
                    authorId,
                    "New comment on your " + target,
                    excerpt(comment.getContent()),
                    NotificationType.COMMENT,
                    comment.getId(),
                    "comment:" + comment.getId() + ":target-author"
            ));
        }

        // A report is a conversation between a researcher and an
        // organization, so the organization side is told about every comment
        // on one — including internal comments, which are theirs. The
        // researcher is reached by the target-author branch above, and only
        // when the comment is public: that branch is what keeps internal
        // discussion internal, so this one is free to run either way.
        if (access.organizationId() != null) {
            eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                    organizationAuthorization.findUserIdsWithPermission(
                            access.organizationId(),
                            OrganizationPermission.VIEW_REPORTS
                    ),
                    authorId,
                    comment.isInternal()
                            ? "New internal note on a report"
                            : "New comment on a report",
                    excerpt(comment.getContent()),
                    NotificationType.COMMENT,
                    comment.getId(),
                    "comment:" + comment.getId() + ":organization"
            ));
        }

        Comment parent = comment.getParentComment();
        if (parent != null) {
            eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                    List.of(parent.getAuthorId()),
                    authorId,
                    "New reply to your comment",
                    excerpt(comment.getContent()),
                    NotificationType.COMMENT,
                    comment.getId(),
                    "comment:" + comment.getId() + ":parent-author"
            ));
        }
    }

    private String excerpt(String content) {
        String collapsed = content.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= 140
                ? collapsed
                : collapsed.substring(0, 139) + "…";
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> findByTarget(
            CommentableType commentableType,
            UUID commentableId,
            UUID parentCommentId,
            int pageNumber,
            int pageSize
    ) {
        TargetAccess access = requireTargetAccess(
                commentableType,
                commentableId
        );

        boolean findingReplies = parentCommentId != null;
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        findingReplies
                                ? Sort.Direction.ASC
                                : Sort.Direction.DESC,
                        "createdAt"
                )
        );

        Page<Comment> comments;
        if (findingReplies) {
            Comment parent = findVisibleComment(
                    parentCommentId,
                    access.canViewInternal()
            );
            requireSameTarget(parent, commentableType, commentableId);
            comments = commentRepository.findReplies(
                    commentableType,
                    commentableId,
                    parentCommentId,
                    access.canViewInternal(),
                    pageable
            );
        } else {
            comments = commentRepository.findRootComments(
                    commentableType,
                    commentableId,
                    access.canViewInternal(),
                    pageable
            );
        }

        return enrich(comments, access.canViewInternal());
    }

    @Override
    @Transactional(readOnly = true)
    public CommentResponse findById(UUID id) {
        Comment comment = findActiveComment(id);
        TargetAccess access = requireTargetAccess(
                comment.getCommentableType(),
                comment.getCommentableId()
        );
        requireVisible(comment, access.canViewInternal());
        return toResponse(comment, access.canViewInternal());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> findMine(
            CommentableType commentableType,
            int pageNumber,
            int pageSize
    ) {
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        return enrich(
                commentRepository.findMine(
                        currentUserId(),
                        commentableType,
                        pageable
                ),
                false
        );
    }

    @Override
    @Transactional
    public CommentResponse update(
            UUID id,
            UpdateCommentRequest request
    ) {
        Comment comment = findActiveComment(id);
        TargetAccess access = requireTargetAccess(
                comment.getCommentableType(),
                comment.getCommentableId()
        );
        requireVisible(comment, access.canViewInternal());
        requireAuthor(comment);
        comment.setContent(request.content().trim());
        return toResponse(
                commentRepository.saveAndFlush(comment),
                access.canViewInternal()
        );
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Comment comment = findActiveComment(id);
        if (comment.isInternal()) {
            TargetAccess access = requireTargetAccess(
                    comment.getCommentableType(),
                    comment.getCommentableId()
            );
            requireVisible(comment, access.canViewInternal());
        }
        UUID userId = currentUserId();
        if (!comment.getAuthorId().equals(userId)
                && !AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the comment author or an admin can delete it"
            );
        }

        commentRepository.softDeleteThread(id, LocalDateTime.now());
    }

    private Page<CommentResponse> enrich(
            Page<Comment> comments,
            boolean includeInternal
    ) {
        if (comments.isEmpty()) {
            return comments.map(comment ->
                    toResponse(comment, includeInternal)
            );
        }

        List<UUID> authorIds = comments.getContent().stream()
                .map(Comment::getAuthorId)
                .distinct()
                .toList();
        Map<UUID, UserProfile> profiles = userProfileRepository
                .findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        Function.identity()
                ));

        List<UUID> commentIds = comments.getContent().stream()
                .map(Comment::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, Long> replyCounts = replyCounts(
                commentIds,
                includeInternal
        );

        return comments.map(comment -> toResponse(
                comment,
                profiles.get(comment.getAuthorId()),
                replyCounts.getOrDefault(comment.getId(), 0L)
        ));
    }

    private CommentResponse toResponse(
            Comment comment,
            boolean includeInternal
    ) {
        UserProfile profile = userProfileRepository
                .findById(comment.getAuthorId())
                .orElse(null);
        long replyCount = comment.getId() == null
                ? 0
                : replyCounts(
                        List.of(comment.getId()),
                        includeInternal
                )
                        .getOrDefault(comment.getId(), 0L);
        return toResponse(comment, profile, replyCount);
    }

    private CommentResponse toResponse(
            Comment comment,
            UserProfile profile,
            long replyCount
    ) {
        return CommentResponse.builder()
                .id(comment.getId())
                .commentableType(comment.getCommentableType())
                .commentableId(comment.getCommentableId())
                .parentCommentId(comment.getParentComment() == null
                        ? null
                        : comment.getParentComment().getId())
                .authorId(comment.getAuthorId())
                .authorName(profile == null ? null : profile.getFullName())
                .authorAvatarUrl(profile == null
                        ? null
                        : profile.getAvatarUrl())
                .content(comment.getContent())
                .internal(comment.isInternal())
                .replyCount(replyCount)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private Map<UUID, Long> replyCounts(
            Collection<UUID> commentIds,
            boolean includeInternal
    ) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository
                .countActiveReplies(commentIds, includeInternal)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
    }

    private TargetAccess requireTargetAccess(
            CommentableType commentableType,
            UUID commentableId
    ) {
        return switch (commentableType) {
            case REPORT -> {
                ReportDiscussionAccess reportAccess =
                        reportService.requireDiscussionAccess(commentableId);
                yield new TargetAccess(
                        reportAccess.canViewInternal(),
                        reportAccess.canComment(),
                        reportAccess.canCreateInternal(),
                        reportAccess.reporterId(),
                        reportAccess.organizationId()
                );
            }
            case PROBLEM -> {
                var problem = problemRepository.findPublicById(commentableId)
                        .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC.withAuthor(problem.getAuthorId());
            }
            case SOLUTION -> {
                var solution = solutionRepository
                    .findByIdAndReviewStatusInAndDeletedAtIsNull(
                            commentableId,
                            PUBLIC_SOLUTION_STATUSES
                    )
                    .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC.withAuthor(solution.getAuthorId());
            }
            case PROGRAM -> {
                programRepository
                    .findByIdAndStateAndSubmissionStateAndVisibilityAndDeletedAtIsNull(
                            commentableId,
                            ProgramState.ACTIVE,
                            SubmissionState.APPROVED,
                            Visibility.PUBLIC
                    )
                    .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC;
            }
            case SHOWCASE -> {
                var showcase = showCasesRepository
                        .findByIdAndReviewStatusAndDeletedAtIsNull(
                                commentableId,
                                kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                        )
                        .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC.withAuthor(
                        showcase.getAuthor() == null
                                ? null
                                : showcase.getAuthor().getId()
                );
            }
        };
    }

    private void validateInternalComment(
            CreateCommentRequest request,
            TargetAccess access
    ) {
        if (!request.internal()) {
            return;
        }
        if (request.commentableType() != CommentableType.REPORT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Internal comments are only supported for reports"
            );
        }
        if (!access.canCreateInternal()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only an admin or a company triage member can create internal report comments"
            );
        }
    }

    private void requireCanComment(TargetAccess access) {
        if (!access.canComment()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to comment on this report"
            );
        }
    }

    private void requireSameTarget(
            Comment parent,
            CreateCommentRequest request
    ) {
        requireSameTarget(
                parent,
                request.commentableType(),
                request.commentableId()
        );
    }

    private void requireSameTarget(
            Comment parent,
            CommentableType commentableType,
            UUID commentableId
    ) {
        if (parent.getCommentableType() != commentableType
                || !parent.getCommentableId().equals(commentableId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Parent comment must belong to the same target"
            );
        }
    }

    private void requireAuthor(Comment comment) {
        if (!comment.getAuthorId().equals(currentUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the comment author can edit it"
            );
        }
    }

    private Comment findActiveComment(UUID id) {
        return commentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(this::commentNotFound);
    }

    private Comment findVisibleComment(
            UUID id,
            boolean includeInternal
    ) {
        Comment comment = findActiveComment(id);
        requireVisible(comment, includeInternal);
        return comment;
    }

    private void requireVisible(
            Comment comment,
            boolean includeInternal
    ) {
        if (comment.isInternal() && !includeInternal) {
            throw commentNotFound();
        }
    }

    private UUID currentUserId() {
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

    private ResourceNotFoundException targetNotFound() {
        return new ResourceNotFoundException("Comment target not found");
    }

    private ResourceNotFoundException commentNotFound() {
        return new ResourceNotFoundException("Comment not found");
    }

    /**
     * @param authorId whoever wrote the thing being commented on, so they can
     *                 be told. Taken from the entity the access check already
     *                 loaded rather than fetched again. Null where the target
     *                 has no single author to notify — a program belongs to an
     *                 organization, not a person — or where working out who
     *                 may hear about the comment needs more than an author,
     *                 as on a report.
     */
    private record TargetAccess(
            boolean canViewInternal,
            boolean canComment,
            boolean canCreateInternal,
            UUID authorId,
            UUID organizationId
    ) {
        private static final TargetAccess PUBLIC =
                new TargetAccess(false, true, false, null, null);

        private TargetAccess withAuthor(UUID authorId) {
            return new TargetAccess(
                    canViewInternal,
                    canComment,
                    canCreateInternal,
                    authorId,
                    organizationId
            );
        }
    }
}
