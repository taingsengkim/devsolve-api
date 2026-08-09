package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemAcceptedSolution;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.AcceptedSolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SolutionServiceImplTest {

    @Mock private SolutionRepository solutionRepository;
    @Mock private SolutionRevisionRepository revisionRepository;
    @Mock private SolutionAttachmentRepository attachmentRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private ProblemService problemService;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private AttachmentValidator attachmentValidator;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private FollowNotificationService followNotificationService;

    private SolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SolutionServiceImpl(
                solutionRepository,
                revisionRepository,
                attachmentRepository,
                problemRepository,
                problemService,
                userProfileRepository,
                voteRepository,
                commentRepository,
                attachmentValidator,
                objectStorageService,
                followNotificationService
        );
        lenient().when(solutionRepository.saveAndFlush(any(Solution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(revisionRepository.saveAndFlush(any(SolutionRevision.class)))
                .thenAnswer(invocation -> {
                    SolutionRevision revision = invocation.getArgument(0);
                    if (revision.getId() == null) {
                        revision.setId(UUID.randomUUID());
                    }
                    return revision;
                });
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicReadsUsePublishedRevisionAndHideModeration() {
        UUID problemId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Problem problem = problem(problemId, UUID.randomUUID(), ProblemStatus.PUBLISHED);
        Solution solution = solution(problem, authorId);
        SolutionRevision published = revision(solution, 1, ReviewStatus.APPROVED, "Published summary");
        solution.setCurrentPublishedRevision(published);
        solution.setLatestRevision(published);

        when(problemRepository.findPublicById(problemId)).thenReturn(Optional.of(problem));
        when(solutionRepository
                .findAllByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                        eq(problemId),
                        any(Pageable.class)
                )).thenReturn(new PageImpl<>(List.of(solution)));
        stubAuthor(authorId);

        SolutionResponse response = service
                .getSolutionsByProblemId(problemId, 0, 20)
                .getContent()
                .getFirst();

        assertEquals("Published summary", response.summary());
        assertNull(response.moderation());
    }

    @Test
    void editingApprovedSolutionCreatesPendingRevisionAndKeepsPublishedRevision() {
        UUID authorId = UUID.randomUUID();
        Problem problem = problem(UUID.randomUUID(), UUID.randomUUID(), ProblemStatus.PUBLISHED);
        Solution solution = solution(problem, authorId);
        solution.setVersion(7);
        SolutionRevision published = revision(solution, 3, ReviewStatus.APPROVED, "Published summary");
        solution.setCurrentPublishedRevision(published);
        solution.setLatestRevision(published);
        authenticate(authorId, false);
        when(solutionRepository.findByIdAndDeletedAtIsNull(solution.getId()))
                .thenReturn(Optional.of(solution));
        stubAuthor(authorId);

        SolutionResponse response = service.updateSolution(
                solution.getId(),
                new SolutionUpdateRequest(
                        "Improved published solution",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                7
        );

        assertSame(published, solution.getCurrentPublishedRevision());
        assertEquals(4, solution.getLatestRevision().getRevisionNumber());
        assertEquals(ReviewStatus.PENDING, solution.getLatestRevision().getModerationStatus());
        assertEquals(ReviewStatus.PENDING, response.moderation().status());
    }

    @Test
    void approvingPendingEditAtomicallyMovesPublishedPointer() {
        UUID adminId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Problem problem = problem(UUID.randomUUID(), UUID.randomUUID(), ProblemStatus.PUBLISHED);
        Solution solution = solution(problem, authorId);
        SolutionRevision oldPublished = revision(solution, 1, ReviewStatus.APPROVED, "Old summary");
        SolutionRevision pending = revision(solution, 2, ReviewStatus.PENDING, "New summary");
        solution.setCurrentPublishedRevision(oldPublished);
        solution.setLatestRevision(pending);
        authenticate(adminId, true);
        when(solutionRepository.findActiveByIdForUpdate(solution.getId()))
                .thenReturn(Optional.of(solution));
        stubAuthor(authorId);

        SolutionResponse response = service.updateReviewStatus(
                solution.getId(),
                new UpdateSolutionReviewStatusRequest(ReviewStatus.APPROVED, null)
        );

        assertSame(pending, solution.getCurrentPublishedRevision());
        assertEquals(ReviewStatus.APPROVED, response.moderation().status());
        verify(followNotificationService).notifyFollowers(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectingEditLeavesPreviouslyApprovedRevisionVisible() {
        UUID adminId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Problem problem = problem(UUID.randomUUID(), UUID.randomUUID(), ProblemStatus.PUBLISHED);
        Solution solution = solution(problem, authorId);
        SolutionRevision published = revision(solution, 1, ReviewStatus.APPROVED, "Published");
        SolutionRevision pending = revision(solution, 2, ReviewStatus.PENDING, "Rejected edit");
        solution.setCurrentPublishedRevision(published);
        solution.setLatestRevision(pending);
        authenticate(adminId, true);
        when(solutionRepository.findActiveByIdForUpdate(solution.getId()))
                .thenReturn(Optional.of(solution));
        stubAuthor(authorId);

        service.updateReviewStatus(
                solution.getId(),
                new UpdateSolutionReviewStatusRequest(
                        ReviewStatus.REJECTED,
                        "Needs reproducible verification"
                )
        );

        assertSame(published, solution.getCurrentPublishedRevision());
        assertEquals(ReviewStatus.REJECTED, pending.getModerationStatus());
        assertEquals("Needs reproducible verification", pending.getRejectionReason());
    }

    @Test
    void acceptanceLivesOnProblemAndDoesNotChangeModeration() {
        UUID ownerId = UUID.randomUUID();
        Problem problem = problem(UUID.randomUUID(), ownerId, ProblemStatus.PUBLISHED);
        Solution solution = solution(problem, UUID.randomUUID());
        SolutionRevision approved = revision(solution, 1, ReviewStatus.APPROVED, "Approved");
        solution.setCurrentPublishedRevision(approved);
        solution.setLatestRevision(approved);
        authenticate(ownerId, false);
        when(problemRepository.findActiveByIdForUpdate(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(solutionRepository.findActiveByIdForUpdate(solution.getId()))
                .thenReturn(Optional.of(solution));

        service.setAcceptedSolution(
                problem.getId(),
                new AcceptedSolutionRequest(solution.getId())
        );

        assertEquals(
                List.of(solution.getId()),
                problem.getAcceptedSolutions().stream()
                        .map(ProblemAcceptedSolution::getSolutionId)
                        .toList()
        );
        assertEquals(ProblemStatus.RESOLVED, problem.getStatus());
        assertEquals(ReviewStatus.APPROVED, approved.getModerationStatus());
    }

    @Test
    void ownerCanAcceptMultipleApprovedSolutions() {
        UUID ownerId = UUID.randomUUID();
        Problem problem = problem(
                UUID.randomUUID(),
                ownerId,
                ProblemStatus.PUBLISHED
        );
        Solution first = approvedSolution(problem, UUID.randomUUID());
        Solution second = approvedSolution(problem, UUID.randomUUID());
        authenticate(ownerId, false);
        when(problemRepository.findActiveByIdForUpdate(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(solutionRepository.findActiveByIdForUpdate(first.getId()))
                .thenReturn(Optional.of(first));
        when(solutionRepository.findActiveByIdForUpdate(second.getId()))
                .thenReturn(Optional.of(second));

        service.setAcceptedSolution(
                problem.getId(),
                new AcceptedSolutionRequest(first.getId())
        );
        service.setAcceptedSolution(
                problem.getId(),
                new AcceptedSolutionRequest(second.getId())
        );

        assertEquals(
                List.of(first.getId(), second.getId()),
                problem.getAcceptedSolutions().stream()
                        .map(ProblemAcceptedSolution::getSolutionId)
                        .toList()
        );
        assertEquals(ProblemStatus.RESOLVED, problem.getStatus());
    }

    @Test
    void sameAuthorCanPostMultipleSolutionsAfterProblemIsResolved() {
        UUID authorId = UUID.randomUUID();
        Problem problem = problem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ProblemStatus.RESOLVED
        );
        addAcceptance(problem, UUID.randomUUID(), problem.getAuthorId());
        authenticate(authorId, false);
        when(problemRepository.findPublicById(problem.getId()))
                .thenReturn(Optional.of(problem));
        stubAuthor(authorId);
        SolutionRequest request = new SolutionRequest(
                "Alternative verified solution",
                "This alternative solution contains enough implementation detail.",
                ApproachType.ALTERNATIVE,
                List.of(),
                List.of(),
                null,
                List.of()
        );

        service.createSolution(problem.getId(), request);
        service.createSolution(problem.getId(), request);

        ArgumentCaptor<SolutionRevision> revisions =
                ArgumentCaptor.forClass(SolutionRevision.class);
        verify(revisionRepository, times(2)).saveAndFlush(revisions.capture());
        Solution first = revisions.getAllValues().get(0).getSolution();
        Solution second = revisions.getAllValues().get(1).getSolution();
        assertNotSame(first, second);
        assertEquals(authorId, first.getAuthorId());
        assertEquals(authorId, second.getAuthorId());
        assertSame(problem, first.getProblem());
        assertSame(problem, second.getProblem());
    }

    @Test
    void removingOnlyOneAcceptanceKeepsProblemResolved() {
        UUID ownerId = UUID.randomUUID();
        Problem problem = problem(UUID.randomUUID(), ownerId, ProblemStatus.RESOLVED);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        addAcceptance(problem, firstId, ownerId);
        addAcceptance(problem, secondId, ownerId);
        authenticate(ownerId, false);
        when(problemRepository.findActiveByIdForUpdate(problem.getId()))
                .thenReturn(Optional.of(problem));

        service.removeAcceptedSolution(problem.getId(), firstId);

        assertEquals(
                List.of(secondId),
                problem.getAcceptedSolutions().stream()
                        .map(ProblemAcceptedSolution::getSolutionId)
                        .toList()
        );
        assertEquals(ProblemStatus.RESOLVED, problem.getStatus());

        service.removeAcceptedSolution(problem.getId(), secondId);

        assertTrue(problem.getAcceptedSolutions().isEmpty());
        assertEquals(ProblemStatus.PUBLISHED, problem.getStatus());
    }

    private Problem problem(UUID id, UUID authorId, ProblemStatus status) {
        return Problem.builder()
                .id(id)
                .authorId(authorId)
                .categoryId(UUID.randomUUID())
                .title("Spring Boot authorization problem")
                .description("A sufficiently detailed problem description for test coverage.")
                .status(status)
                .build();
    }

    private Solution solution(Problem problem, UUID authorId) {
        return Solution.builder()
                .id(UUID.randomUUID())
                .problem(problem)
                .authorId(authorId)
                .build();
    }

    private Solution approvedSolution(Problem problem, UUID authorId) {
        Solution solution = solution(problem, authorId);
        SolutionRevision approved = revision(
                solution,
                1,
                ReviewStatus.APPROVED,
                "Approved solution"
        );
        solution.setCurrentPublishedRevision(approved);
        solution.setLatestRevision(approved);
        return solution;
    }

    private void addAcceptance(
            Problem problem,
            UUID solutionId,
            UUID acceptedBy
    ) {
        problem.getAcceptedSolutions().add(
                ProblemAcceptedSolution.builder()
                        .id(UUID.randomUUID())
                        .problem(problem)
                        .solutionId(solutionId)
                        .acceptedBy(acceptedBy)
                        .acceptedAt(java.time.LocalDateTime.now())
                        .build()
        );
    }

    private SolutionRevision revision(
            Solution solution,
            int number,
            ReviewStatus status,
            String summary
    ) {
        return SolutionRevision.builder()
                .id(UUID.randomUUID())
                .solution(solution)
                .revisionNumber(number)
                .summary(summary)
                .bodyMarkdown("This body contains enough detail to explain and verify the solution.")
                .approachType(ApproachType.FIX)
                .verificationSteps(new ArrayList<>())
                .testedWith(new ArrayList<>())
                .resources(new ArrayList<>())
                .attachments(new ArrayList<>())
                .moderationStatus(status)
                .build();
    }

    private void stubAuthor(UUID authorId) {
        UserProfile profile = new UserProfile();
        profile.setId(authorId);
        profile.setFullName("Solution Author");
        when(userProfileRepository.findById(authorId)).thenReturn(Optional.of(profile));
    }

    private void authenticate(UUID subject, boolean admin) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        List<SimpleGrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities)
        );
    }
}
