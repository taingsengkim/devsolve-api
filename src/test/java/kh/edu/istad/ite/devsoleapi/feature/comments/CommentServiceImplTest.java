package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private kh.edu.istad.ite.devsoleapi.feature.organization
            .OrganizationAuthorizationService organizationAuthorization;

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
                organizationAuthorization,
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
        when(commentRepository.saveAndFlush(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment saved = invocation.getArgument(0);
                    saved.setId(commentId);
                    saved.setCreatedAt(LocalDateTime.now());
                    saved.setUpdatedAt(LocalDateTime.now());
                    return saved;
                });
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.empty());
        when(commentRepository.countActiveReplies(
                List.of(commentId),
                false
        ))
                .thenReturn(List.of());

        CommentResponse result = service.create(new CreateCommentRequest(
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
                () -> service.create(new CreateCommentRequest(
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
        when(userProfileRepository.findAllById(List.of(userId)))
                .thenReturn(List.of());
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
                () -> service.create(new CreateCommentRequest(
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
                () -> service.create(new CreateCommentRequest(
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
                new CreateCommentRequest(
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
        verify(userProfileRepository, never()).findById(any());
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
                () -> service.create(new CreateCommentRequest(
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
    void adminCanSoftDeleteEntireReplyThread() {
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

        service.delete(commentId);

        verify(commentRepository).softDeleteThread(
                eq(commentId),
                any(LocalDateTime.class)
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
        when(userProfileRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
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
