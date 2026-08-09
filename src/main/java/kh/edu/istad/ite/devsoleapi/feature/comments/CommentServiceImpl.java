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

        return toResponse(
                commentRepository.saveAndFlush(comment),
                access.canViewInternal()
        );
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
                        reportAccess.canCreateInternal()
                );
            }
            case PROBLEM -> {
                problemRepository.findPublicById(commentableId)
                        .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC;
            }
            case SOLUTION -> {
                solutionRepository
                    .findByIdAndReviewStatusInAndDeletedAtIsNull(
                            commentableId,
                            PUBLIC_SOLUTION_STATUSES
                    )
                    .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC;
            }
            case PROGRAM -> {
                programRepository
                    .findByIdAndStateAndSubmissionStateAndVisibility(
                            commentableId,
                            ProgramState.ACTIVE,
                            SubmissionState.APPROVED,
                            Visibility.PUBLIC
                    )
                    .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC;
            }
            case SHOWCASE -> {
                showCasesRepository
                        .findByIdAndReviewStatusAndDeletedAtIsNull(
                                commentableId,
                                kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                        )
                        .orElseThrow(this::targetNotFound);
                yield TargetAccess.PUBLIC;
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

    private record TargetAccess(
            boolean canViewInternal,
            boolean canComment,
            boolean canCreateInternal
    ) {
        private static final TargetAccess PUBLIC =
                new TargetAccess(false, true, false);
    }
}
