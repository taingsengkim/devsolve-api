package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentRemovalReason;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentSort;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportDiscussionAccess;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.Solution;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ReportService reportService;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private kh.edu.istad.ite.devsoleapi.feature.organization
            .OrganizationAuthorizationService organizationAuthorization;

    @Mock
    private CommentRateLimiter rateLimiter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(
                commentRepository,
                reportService,
                problemRepository,
                solutionRepository,
                programRepository,
                showCasesRepository,
                userProfileRepository,
                voteRepository,
                organizationAuthorization,
                rateLimiter,
                eventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProblemCommentValidatesTargetAndTrimsContent() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(userId, false);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        stubSavedComment(commentId);

        CommentResponse result = service.create(request(
                CommentableType.PROBLEM,
                problemId,
                "  This fixed the issue.  ",
                null,
                false
        ));

        ArgumentCaptor<Comment> commentCaptor =
                ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository)
                .saveAndFlush(commentCaptor.capture());
        assertEquals(
                "This fixed the issue.",
                commentCaptor.getValue().getContent()
        );
        assertEquals(userId, result.getAuthorId());
        assertEquals(problemId, result.getCommentableId());
    }

    @Test
    void createRejectsUnsafeHtml() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        authenticate(userId, false);
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.PROBLEM,
                        problemId,
                        "<script>alert('xss')</script>",
                        null,
                        false
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsAnImmediateRepostOfTheSameText() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        authenticate(userId, false);
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.existsRecentDuplicate(
                eq(userId),
                eq(CommentableType.PROBLEM),
                eq(problemId),
                eq("Same thing twice"),
                any(LocalDateTime.class)
        )).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.PROBLEM,
                        problemId,
                        "Same thing twice",
                        null,
                        false
                ))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
        // The duplicate must not spend the author's burst allowance.
        verify(rateLimiter, never()).checkBurst(any());
    }

    @Test
    void createRejectsParentFromAnotherTarget() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        authenticate(userId, false);

        Comment parent = comment(
                parentId,
                UUID.randomUUID(),
                userId,
                CommentableType.PROBLEM
        );
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parent));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.PROBLEM,
                        problemId,
                        "Reply",
                        parentId,
                        false
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void replyToAReplyIsFlattenedOntoTheThreadRoot() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(userId, false);

        Comment root = comment(
                rootId,
                problemId,
                UUID.randomUUID(),
                CommentableType.PROBLEM
        );
        Comment reply = comment(
                replyId,
                problemId,
                UUID.randomUUID(),
                CommentableType.PROBLEM
        );
        reply.setParentComment(root);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findByIdAndDeletedAtIsNull(replyId))
                .thenReturn(Optional.of(reply));
        stubSavedComment(commentId);

        service.create(request(
                CommentableType.PROBLEM,
                problemId,
                "Answering the reply",
                replyId,
                false
        ));

        ArgumentCaptor<Comment> commentCaptor =
                ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(commentCaptor.capture());
        assertSame(
                root,
                commentCaptor.getValue().getParentComment(),
                "a third-level reply must be re-pointed at the thread root, "
                        + "or nothing ever reads it back"
        );
    }

    @Test
    void replyingToARemovedCommentIsRejected() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        authenticate(userId, false);

        Comment parent = comment(
                parentId,
                problemId,
                UUID.randomUUID(),
                CommentableType.PROBLEM
        );
        parent.setRemovedAt(LocalDateTime.now());
        parent.setRemovalReason(CommentRemovalReason.AUTHOR);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parent));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.PROBLEM,
                        problemId,
                        "Reply to nothing",
                        parentId,
                        false
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rootCommentsArePagedNewestFirstAndContainReplyCount() {
        UUID problemId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Comment root = comment(
                UUID.randomUUID(),
                problemId,
                userId,
                CommentableType.PROBLEM
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findRootComments(
                        eq(CommentableType.PROBLEM),
                        eq(problemId),
                        eq(false),
                        any(Pageable.class)
                ))
                .thenReturn(new PageImpl<>(List.of(root)));
        when(commentRepository.countActiveReplies(
                List.of(root.getId()),
                false
        ))
                .thenReturn(Collections.singletonList(
                        new Object[]{root.getId(), 2L}
                ));

        Page<CommentResponse> result = service.findByTarget(
                CommentableType.PROBLEM,
                problemId,
                null,
                null,
                0,
                20
        );

        verify(commentRepository).findRootComments(
                        eq(CommentableType.PROBLEM),
                        eq(problemId),
                        eq(false),
                        pageableCaptor.capture()
                );
        assertTrue(pageableCaptor.getValue()
                .getSort()
                .getOrderFor("createdAt")
                .isDescending());
        assertEquals(2L, result.getContent().getFirst().getReplyCount());
    }

    @Test
    void listingBreaksTimestampTiesOnIdSoPagesCannotRepeat() {
        UUID problemId = UUID.randomUUID();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findRootComments(
                any(CommentableType.class),
                any(UUID.class),
                eq(false),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.findByTarget(
                CommentableType.PROBLEM,
                problemId,
                null,
                CommentSort.OLDEST,
                0,
                20
        );

        verify(commentRepository).findRootComments(
                any(CommentableType.class),
                any(UUID.class),
                eq(false),
                pageableCaptor.capture()
        );
        assertTrue(pageableCaptor.getValue()
                .getSort()
                .getOrderFor("createdAt")
                .isAscending());
        assertTrue(pageableCaptor.getValue()
                .getSort()
                .getOrderFor("id")
                .isAscending());
    }

    @Test
    void topSortGoesThroughTheScoredQuery() {
        UUID problemId = UUID.randomUUID();
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findRootCommentsByScore(
                eq(CommentableType.PROBLEM),
                eq(problemId),
                eq(false),
                eq(VoteType.COMMENT),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.findByTarget(
                CommentableType.PROBLEM,
                problemId,
                null,
                CommentSort.TOP,
                0,
                20
        );

        verify(commentRepository).findRootCommentsByScore(
                eq(CommentableType.PROBLEM),
                eq(problemId),
                eq(false),
                eq(VoteType.COMMENT),
                any(Pageable.class)
        );
        verify(commentRepository, never()).findRootComments(
                any(CommentableType.class),
                any(UUID.class),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    void solutionTargetOnlyAcceptsPublicReviewStatuses() {
        UUID solutionId = UUID.randomUUID();
        when(solutionRepository
                .findByIdAndReviewStatusInAndDeletedAtIsNull(
                        solutionId,
                        List.of(ReviewStatus.APPROVED)
                ))
                .thenReturn(Optional.of(mock(Solution.class)));
        when(commentRepository.findRootComments(
                        eq(CommentableType.SOLUTION),
                        eq(solutionId),
                        eq(false),
                        any(Pageable.class)
                ))
                .thenReturn(Page.empty());

        service.findByTarget(
                CommentableType.SOLUTION,
                solutionId,
                null,
                null,
                0,
                20
        );

        verify(solutionRepository)
                .findByIdAndReviewStatusInAndDeletedAtIsNull(
                        solutionId,
                        List.of(ReviewStatus.APPROVED)
                );
    }

    @Test
    void showcaseCommentsOnlyTargetApprovedShowcases() {
        UUID showcaseId = UUID.randomUUID();
        when(showCasesRepository
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        showcaseId,
                        kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                ))
                .thenReturn(Optional.of(mock(ShowCases.class)));
        when(commentRepository.findRootComments(
                eq(CommentableType.SHOWCASE),
                eq(showcaseId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.findByTarget(
                CommentableType.SHOWCASE,
                showcaseId,
                null,
                null,
                0,
                20
        );

        verify(showCasesRepository)
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        showcaseId,
                        kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                );
    }

    @Test
    void internalCommentsAreRejectedForNonReportTargets() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        authenticate(userId, false);
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.PROBLEM,
                        problemId,
                        "Private note",
                        null,
                        true
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void reporterCannotCreateInternalReportComment() {
        UUID userId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        authenticate(userId, false);
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(false, true, false, null, null));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.REPORT,
                        reportId,
                        "Should remain hidden",
                        null,
                        true
                ))
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void companyTriagerCanCreateInternalReportComment() {
        UUID userId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(userId, false);
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(true, true, true, null, null));
        stubSavedComment(commentId, true);

        CommentResponse result = service.create(
                request(
                        CommentableType.REPORT,
                        reportId,
                        "Likely duplicate; verify internally.",
                        null,
                        true
                )
        );

        ArgumentCaptor<Comment> commentCaptor =
                ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository)
                .saveAndFlush(commentCaptor.capture());
        assertTrue(commentCaptor.getValue().isInternal());
        assertTrue(result.isInternal());
    }

    @Test
    void reportListingUsesCallerInternalVisibility() {
        UUID reportId = UUID.randomUUID();
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(false, true, false, null, null));
        when(commentRepository.findRootComments(
                eq(CommentableType.REPORT),
                eq(reportId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.findByTarget(
                CommentableType.REPORT,
                reportId,
                null,
                null,
                0,
                20
        );

        verify(commentRepository).findRootComments(
                eq(CommentableType.REPORT),
                eq(reportId),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    void internalCommentIsHiddenFromReporterById() {
        UUID reportId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment internalComment = comment(
                commentId,
                reportId,
                UUID.randomUUID(),
                CommentableType.REPORT
        );
        internalComment.setInternal(true);
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(internalComment));
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(false, true, false, null, null));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.findById(commentId)
        );
        verify(userProfileRepository, never()).findAllById(any());
    }

    @Test
    void publicReplyCannotBeAttachedToInternalThread() {
        UUID userId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        authenticate(userId, false);
        Comment parent = comment(
                parentId,
                reportId,
                UUID.randomUUID(),
                CommentableType.REPORT
        );
        parent.setInternal(true);
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(true, true, true, null, null));
        when(commentRepository.findByIdAndDeletedAtIsNull(parentId))
                .thenReturn(Optional.of(parent));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request(
                        CommentableType.REPORT,
                        reportId,
                        "Make this public",
                        parentId,
                        false
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void onlyAuthorCanEditComment() {
        UUID commentId = UUID.randomUUID();
        authenticate(UUID.randomUUID(), false);
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommentableType.PROBLEM
        );
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(problemRepository.findPublicById(comment.getCommentableId()))
                .thenReturn(Optional.of(mock(Problem.class)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(
                        commentId,
                        new UpdateCommentRequest("Changed")
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void editWithinTheGracePeriodIsNotMarkedAsEdited() {
        UUID authorId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(authorId, false);
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                authorId,
                CommentableType.PROBLEM
        );
        stubEditableComment(comment);

        CommentResponse result = service.update(
                commentId,
                new UpdateCommentRequest("Fixed my typo")
        );

        assertFalse(result.isEdited());
        assertNull(comment.getEditedAt());
        assertEquals("Fixed my typo", comment.getContent());
    }

    @Test
    void editAfterTheGracePeriodIsMarkedAsEdited() {
        UUID authorId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(authorId, false);
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                authorId,
                CommentableType.PROBLEM
        );
        comment.setCreatedAt(LocalDateTime.now().minusHours(2));
        stubEditableComment(comment);

        CommentResponse result = service.update(
                commentId,
                new UpdateCommentRequest("Rewritten after people replied")
        );

        assertTrue(result.isEdited());
        assertEquals(comment.getEditedAt(), result.getEditedAt());
    }

    @Test
    void deletingACommentWithRepliesLeavesATombstone() {
        UUID authorId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(authorId, false);
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                authorId,
                CommentableType.PROGRAM
        );
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentRepository.countLiveChildren(commentId)).thenReturn(3L);

        service.delete(commentId);

        verify(commentRepository, never())
                .softDelete(any(), any(LocalDateTime.class));
        verify(commentRepository).saveAndFlush(comment);
        assertTrue(comment.isRemoved());
        assertEquals(CommentRemovalReason.AUTHOR, comment.getRemovalReason());
        assertEquals(
                "",
                comment.getContent(),
                "a delete that leaves the words in the database is not a delete"
        );
    }

    @Test
    void deletingACommentWithNoRepliesRemovesTheRow() {
        UUID adminId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(adminId, true);
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommentableType.PROGRAM
        );
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));
        when(commentRepository.countLiveChildren(commentId)).thenReturn(0L);

        service.delete(commentId);

        verify(commentRepository).softDelete(
                eq(commentId),
                any(LocalDateTime.class)
        );
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void deletingTheLastReplyTakesAnEmptyTombstoneWithIt() {
        UUID authorId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        authenticate(authorId, false);

        Comment root = comment(
                rootId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommentableType.PROGRAM
        );
        root.setRemovedAt(LocalDateTime.now());
        root.setRemovalReason(CommentRemovalReason.AUTHOR);
        Comment reply = comment(
                replyId,
                root.getCommentableId(),
                authorId,
                CommentableType.PROGRAM
        );
        reply.setParentComment(root);

        when(commentRepository.findByIdAndDeletedAtIsNull(replyId))
                .thenReturn(Optional.of(reply));
        when(commentRepository.findByIdAndDeletedAtIsNull(rootId))
                .thenReturn(Optional.of(root));
        when(commentRepository.countLiveChildren(replyId)).thenReturn(0L);
        when(commentRepository.countLiveChildren(rootId)).thenReturn(0L);

        service.delete(replyId);

        verify(commentRepository).softDelete(
                eq(replyId),
                any(LocalDateTime.class)
        );
        verify(commentRepository).softDelete(
                eq(rootId),
                any(LocalDateTime.class)
        );
    }

    @Test
    void aModeratorRemovalKeepsTheRowAndNamesItself() {
        UUID moderatorId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Comment comment = comment(
                commentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                CommentableType.SHOWCASE
        );
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId))
                .thenReturn(Optional.of(comment));

        service.removeByModerator(commentId, moderatorId);

        assertTrue(comment.isRemoved());
        assertEquals(
                CommentRemovalReason.MODERATOR,
                comment.getRemovalReason()
        );
        assertEquals(moderatorId, comment.getRemovedBy());
        verify(commentRepository, never())
                .softDelete(any(), any(LocalDateTime.class));
    }

    @Test
    void aTombstoneShowsNeitherItsTextNorItsAuthor() {
        UUID showcaseId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Comment removed = comment(
                UUID.randomUUID(),
                showcaseId,
                authorId,
                CommentableType.SHOWCASE
        );
        removed.setContent("");
        removed.setRemovedAt(LocalDateTime.now());
        removed.setRemovalReason(CommentRemovalReason.MODERATOR);

        when(showCasesRepository
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        showcaseId,
                        kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                ))
                .thenReturn(Optional.of(mock(ShowCases.class)));
        when(commentRepository.findRootComments(
                eq(CommentableType.SHOWCASE),
                eq(showcaseId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(removed)));

        CommentResponse result = service.findByTarget(
                CommentableType.SHOWCASE,
                showcaseId,
                null,
                null,
                0,
                20
        ).getContent().getFirst();

        assertTrue(result.isRemoved());
        assertNull(result.getContent());
        assertNull(result.getAuthorName());
        assertEquals(
                CommentRemovalReason.MODERATOR,
                result.getRemovalReason()
        );
    }

    @Test
    void theHourlyCapIsCountedFromStoredComments() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        authenticate(userId, false);
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.countByAuthorSince(
                eq(userId),
                any(LocalDateTime.class)
        )).thenReturn(42L);
        stubSavedComment(commentId);

        service.create(request(
                CommentableType.PROBLEM,
                problemId,
                "Within the cap",
                null,
                false
        ));

        verify(rateLimiter).checkSustained(42L);
        verify(rateLimiter).checkBurst(userId);
    }

    @Test
    void aFlattenedReplyStillNotifiesThePersonBeingAnswered() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID rootAuthorId = UUID.randomUUID();
        UUID answeredAuthorId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        authenticate(userId, false);

        Comment root = comment(
                rootId,
                problemId,
                rootAuthorId,
                CommentableType.PROBLEM
        );
        Comment answered = comment(
                replyId,
                problemId,
                answeredAuthorId,
                CommentableType.PROBLEM
        );
        answered.setParentComment(root);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.findByIdAndDeletedAtIsNull(replyId))
                .thenReturn(Optional.of(answered));
        stubSavedComment(UUID.randomUUID());

        service.create(request(
                CommentableType.PROBLEM,
                problemId,
                "Answering the reply",
                replyId,
                false
        ));

        ArgumentCaptor<Object> captor =
                ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        List<UUID> replyNotified = captor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .map(NotificationEvent.class::cast)
                .filter(event -> "New reply to your comment"
                        .equals(event.title()))
                .flatMap(event -> event.recipientIds().stream())
                .toList();

        assertTrue(
                replyNotified.contains(answeredAuthorId),
                "the person actually being answered should be told they were "
                        + "replied to, not folded into the thread digest"
        );
        assertTrue(replyNotified.contains(rootAuthorId));
    }

    private CreateCommentRequest request(
            CommentableType type,
            UUID targetId,
            String content,
            UUID parentCommentId,
            boolean internal
    ) {
        return new CreateCommentRequest(
                type,
                targetId,
                content,
                parentCommentId,
                internal,
                List.of()
        );
    }

    private Comment comment(
            UUID id,
            UUID targetId,
            UUID authorId,
            CommentableType type
    ) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setCommentableType(type);
        comment.setCommentableId(targetId);
        comment.setAuthorId(authorId);
        comment.setContent("Comment");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        return comment;
    }

    private void stubEditableComment(Comment comment) {
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId()))
                .thenReturn(Optional.of(comment));
        when(problemRepository.findPublicById(comment.getCommentableId()))
                .thenReturn(Optional.of(mock(Problem.class)));
        when(commentRepository.saveAndFlush(comment)).thenReturn(comment);
        when(commentRepository.countActiveReplies(
                List.of(comment.getId()),
                false
        )).thenReturn(List.of());
    }

    private void stubSavedComment(UUID commentId) {
        stubSavedComment(commentId, false);
    }

    private void stubSavedComment(
            UUID commentId,
            boolean includeInternal
    ) {
        when(commentRepository.saveAndFlush(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment saved = invocation.getArgument(0);
                    saved.setId(commentId);
                    saved.setCreatedAt(LocalDateTime.now());
                    saved.setUpdatedAt(LocalDateTime.now());
                    return saved;
                });
        when(commentRepository.countActiveReplies(
                List.of(commentId),
                includeInternal
        )).thenReturn(List.of());
    }

    private void authenticate(UUID userId, boolean admin) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        List<SimpleGrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities)
        );
    }
}
