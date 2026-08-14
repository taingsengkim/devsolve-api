package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemResponseEnricherTest {

    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private VoteRepository voteRepository;
    @Mock
    private BookmarkRepository bookmarkRepository;

    private ProblemResponseEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new ProblemResponseEnricher(
                solutionRepository,
                commentRepository,
                voteRepository,
                bookmarkRepository
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanDeleteAProblemThatHasPublishedSolutions() {
        UUID ownerId = UUID.randomUUID();
        Problem problem = problem(ownerId);
        authenticate(ownerId);
        when(solutionRepository.countPublishedByProblemIds(
                List.of(problem.getId())
        )).thenReturn(List.of(count(problem.getId(), 2L)));

        ProblemResponseMetrics metrics = enricher.read(problem);

        assertEquals(2L, metrics.solutionCount());
        assertTrue(metrics.canDelete());
    }

    @Test
    void anotherUserStillCannotDeleteTheProblem() {
        Problem problem = problem(UUID.randomUUID());
        authenticate(UUID.randomUUID());

        ProblemResponseMetrics metrics = enricher.read(problem);

        assertFalse(metrics.canDelete());
    }

    private Problem problem(UUID ownerId) {
        return Problem.builder()
                .id(UUID.randomUUID())
                .authorId(ownerId)
                .categoryId(UUID.randomUUID())
                .title("Answered problem")
                .description("A problem with a published solution")
                .status(ProblemStatus.PUBLISHED)
                .build();
    }

    private IdCountProjection count(UUID id, long total) {
        return new IdCountProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }
}
