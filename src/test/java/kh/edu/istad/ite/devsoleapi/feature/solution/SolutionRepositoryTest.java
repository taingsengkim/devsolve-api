package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SolutionRepositoryTest {

    @Autowired
    private SolutionRepository solutionRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void softDeletingProblemSolutionsHidesThemAndAdvancesTheirVersions() {
        Problem problem = problemRepository.saveAndFlush(Problem.builder()
                .authorId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .title("Problem being deleted")
                .description("Published solutions must be hidden with it")
                .status(ProblemStatus.PUBLISHED)
                .build());
        Solution solution = solutionRepository.saveAndFlush(Solution.builder()
                .problem(problem)
                .authorId(UUID.randomUUID())
                .build());
        Solution secondSolution = solutionRepository.saveAndFlush(
                Solution.builder()
                        .problem(problem)
                        .authorId(UUID.randomUUID())
                        .build()
        );
        Problem unrelatedProblem = problemRepository.saveAndFlush(
                Problem.builder()
                        .authorId(UUID.randomUUID())
                        .categoryId(UUID.randomUUID())
                        .title("Unrelated problem")
                        .description("Its solution must remain available")
                        .status(ProblemStatus.PUBLISHED)
                        .build()
        );
        Solution unrelatedSolution = solutionRepository.saveAndFlush(
                Solution.builder()
                        .problem(unrelatedProblem)
                        .authorId(UUID.randomUUID())
                        .build()
        );
        long previousVersion = solution.getVersion();
        LocalDateTime deletedAt = LocalDateTime.now();

        assertEquals(
                2,
                solutionRepository.softDeleteAllByProblemId(
                        problem.getId(),
                        deletedAt
                )
        );
        entityManager.clear();

        assertTrue(solutionRepository.findById(solution.getId()).isEmpty());
        assertTrue(
                solutionRepository.findById(secondSolution.getId()).isEmpty()
        );
        assertTrue(
                solutionRepository.findById(unrelatedSolution.getId()).isPresent()
        );
        Object[] stored = (Object[]) entityManager.createNativeQuery("""
                        select deleted_at, updated_at, version
                        from solutions
                        where id = :id
                        """)
                .setParameter("id", solution.getId())
                .getSingleResult();
        assertNotNull(stored[0]);
        assertEquals(stored[0], stored[1]);
        assertEquals(previousVersion + 1, ((Number) stored[2]).longValue());
    }
}
