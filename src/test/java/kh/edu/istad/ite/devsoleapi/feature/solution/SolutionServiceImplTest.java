package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolutionServiceImplTest {

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ProblemRepository problemRepository;

    private SolutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SolutionServiceImpl(
                solutionRepository,
                problemRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicProblemSolutionsOnlyQueryApprovedAndAccepted() {
        UUID problemId = UUID.randomUUID();
        Problem problem = problem(
                problemId,
                UUID.randomUUID(),
                ProblemStatus.PUBLISHED
        );
        Solution approved = solution(
                problem,
                UUID.randomUUID(),
                ReviewStatus.APPROVED
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.of(problem));
        when(solutionRepository
                .findAllByProblem_IdAndReviewStatusInAndDeletedAtIsNull(
                        eq(problemId),
                        eq(List.of(
                                ReviewStatus.APPROVED,
                                ReviewStatus.ACCEPTED
                        )),
                        any(Pageable.class)
                ))
                .thenReturn(new PageImpl<>(List.of(approved)));

        Page<SolutionResponse> result =
                service.getSolutionsByProblemId(
                        problemId,
                        0,
                        20
                );

        assertEquals(1, result.getNumberOfElements());
        assertEquals(
                ReviewStatus.APPROVED,
                result.getContent().getFirst().reviewStatus()
        );
        verify(solutionRepository)
                .findAllByProblem_IdAndReviewStatusInAndDeletedAtIsNull(
                        eq(problemId),
                        eq(List.of(
                                ReviewStatus.APPROVED,
                                ReviewStatus.ACCEPTED
                        )),
                        pageableCaptor.capture()
                );
        assertEquals(
                Sort.Direction.ASC,
                pageableCaptor.getValue()
                        .getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void mineReturnsAllOwnerStatusesNewestFirst() {
        UUID ownerId = UUID.randomUUID();
        Solution rejected = solution(
                problem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ProblemStatus.PUBLISHED
                ),
                ownerId,
                ReviewStatus.REJECTED
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        authenticate(ownerId, false);
        when(solutionRepository
                .findAllByAuthorIdAndDeletedAtIsNull(
                        eq(ownerId),
                        any(Pageable.class)
                ))
                .thenReturn(new PageImpl<>(List.of(rejected)));

        Page<SolutionResponse> result =
                service.getMine(1, 10);

        assertEquals(
                ReviewStatus.REJECTED,
                result.getContent().getFirst().reviewStatus()
        );
        verify(solutionRepository)
                .findAllByAuthorIdAndDeletedAtIsNull(
                        eq(ownerId),
                        pageableCaptor.capture()
                );
        assertEquals(
                Sort.Direction.DESC,
                pageableCaptor.getValue()
                        .getSort()
                        .getOrderFor("updatedAt")
                        .getDirection()
        );
    }

    @Test
    void editingRejectedSolutionResubmitsItForReview() {
        UUID ownerId = UUID.randomUUID();
        UUID solutionId = UUID.randomUUID();
        Solution rejected = solution(
                problem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ProblemStatus.PUBLISHED
                ),
                ownerId,
                ReviewStatus.REJECTED
        );
        rejected.setId(solutionId);
        rejected.setReviewedBy(UUID.randomUUID());
        rejected.setReviewedAt(LocalDateTime.now());
        rejected.setRejectionReason("Needs more detail");
        SolutionUpdateRequest request =
                new SolutionUpdateRequest(
                        "Improved explanation",
                        null,
                        null
                );

        authenticate(ownerId, false);
        when(solutionRepository.findByIdAndDeletedAtIsNull(solutionId))
                .thenReturn(Optional.of(rejected));
        when(solutionRepository.save(rejected))
                .thenReturn(rejected);

        SolutionResponse response =
                service.updateSolution(solutionId, request);

        assertEquals(
                ReviewStatus.PENDING,
                response.reviewStatus()
        );
        assertEquals(
                "Improved explanation",
                rejected.getDescription()
        );
        assertNull(rejected.getReviewedBy());
        assertNull(rejected.getReviewedAt());
        assertNull(rejected.getRejectionReason());
    }

    @Test
    void adminCanRejectPendingSolutionWithReason() {
        UUID adminId = UUID.randomUUID();
        UUID solutionId = UUID.randomUUID();
        Solution pending = solution(
                problem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ProblemStatus.PUBLISHED
                ),
                UUID.randomUUID(),
                ReviewStatus.PENDING
        );
        pending.setId(solutionId);
        UpdateSolutionReviewStatusRequest request =
                new UpdateSolutionReviewStatusRequest(
                        ReviewStatus.REJECTED,
                        "  The explanation is incomplete.  "
                );

        authenticate(adminId, true);
        when(solutionRepository.findByIdAndDeletedAtIsNull(solutionId))
                .thenReturn(Optional.of(pending));
        when(solutionRepository.save(pending)).thenReturn(pending);

        SolutionResponse response = service.updateReviewStatus(
                solutionId,
                request
        );

        assertEquals(
                ReviewStatus.REJECTED,
                response.reviewStatus()
        );
        assertEquals(adminId, pending.getReviewedBy());
        assertEquals(
                "The explanation is incomplete.",
                pending.getRejectionReason()
        );
    }

    @Test
    void acceptingAnotherApprovedSolutionIsAllowed() {
        UUID problemOwnerId = UUID.randomUUID();
        UUID solutionId = UUID.randomUUID();
        Problem problem = problem(
                UUID.randomUUID(),
                problemOwnerId,
                ProblemStatus.RESOLVED
        );
        Solution approved = solution(
                problem,
                UUID.randomUUID(),
                ReviewStatus.APPROVED
        );
        approved.setId(solutionId);

        authenticate(problemOwnerId, false);
        when(solutionRepository.findByIdAndDeletedAtIsNull(solutionId))
                .thenReturn(Optional.of(approved));
        when(solutionRepository.save(approved))
                .thenReturn(approved);

        SolutionResponse response =
                service.acceptSolution(solutionId);

        assertEquals(
                ReviewStatus.ACCEPTED,
                response.reviewStatus()
        );
        assertEquals(ProblemStatus.RESOLVED, problem.getStatus());
        verify(problemRepository).save(problem);
    }

    @Test
    void deletingOneAcceptedSolutionKeepsProblemResolvedWhenAnotherExists() {
        UUID adminId = UUID.randomUUID();
        UUID solutionId = UUID.randomUUID();
        Problem problem = problem(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ProblemStatus.RESOLVED
        );
        Solution accepted = solution(
                problem,
                UUID.randomUUID(),
                ReviewStatus.ACCEPTED
        );
        accepted.setId(solutionId);

        authenticate(adminId, true);
        when(solutionRepository.findByIdAndDeletedAtIsNull(solutionId))
                .thenReturn(Optional.of(accepted));
        when(solutionRepository
                .existsByProblem_IdAndReviewStatusAndDeletedAtIsNullAndIdNot(
                        problem.getId(),
                        ReviewStatus.ACCEPTED,
                        solutionId
                )).thenReturn(true);
        when(solutionRepository.save(accepted))
                .thenReturn(accepted);

        service.deleteSolution(solutionId);

        assertEquals(ProblemStatus.RESOLVED, problem.getStatus());
        verify(solutionRepository).save(accepted);
    }

    @Test
    void adminQueueFiltersByReviewStatus() {
        Solution pending = solution(
                problem(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ProblemStatus.PUBLISHED
                ),
                UUID.randomUUID(),
                ReviewStatus.PENDING
        );

        authenticate(UUID.randomUUID(), true);
        when(solutionRepository.findForModeration(
                eq(ReviewStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(pending)));

        Page<SolutionResponse> result =
                service.getForModeration(
                        ReviewStatus.PENDING,
                        0,
                        20
                );

        assertEquals(1, result.getNumberOfElements());
        assertEquals(
                pending.getId(),
                result.getContent().getFirst().id()
        );
    }

    private Problem problem(
            UUID id,
            UUID authorId,
            ProblemStatus status
    ) {
        return Problem.builder()
                .id(id)
                .authorId(authorId)
                .title("Problem title")
                .description("Problem description")
                .status(status)
                .build();
    }

    private Solution solution(
            Problem problem,
            UUID authorId,
            ReviewStatus reviewStatus
    ) {
        return Solution.builder()
                .id(UUID.randomUUID())
                .problem(problem)
                .authorId(authorId)
                .description("Solution explanation")
                .reviewStatus(reviewStatus)
                .build();
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
