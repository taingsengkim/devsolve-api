package kh.edu.istad.ite.devsoleapi.feature.problem;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ProblemRepositoryTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProblemTechnologyRepository technologyRepository;

    @Test
    void listsPublishedProblemsWhenAllOptionalFiltersAreAbsent() {
        Problem published = saveProblem(ProblemStatus.PUBLISHED);
        saveProblem(ProblemStatus.DRAFT);

        Page<Problem> result = problemRepository.findPublished(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(published.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void filtersPublishedProblemsByTechnologyCaseInsensitively() {
        Problem published = saveProblem(ProblemStatus.PUBLISHED);
        technologyRepository.saveAndFlush(ProblemTechnology.builder()
                .problem(published)
                .name("Node.js")
                .version("18")
                .build());

        Page<Problem> result = problemRepository.findPublished(
                null,
                null,
                null,
                "node.js",
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(published.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void softDeletedProblemIsExcludedFromNormalQueries() {
        Problem problem = saveProblem(ProblemStatus.DRAFT);
        problem.setDeletedAt(Instant.now());
        problemRepository.saveAndFlush(problem);
        entityManager.clear();

        assertTrue(problemRepository.findById(problem.getId()).isEmpty());
        assertTrue(
                problemRepository.findActiveById(problem.getId()).isEmpty()
        );
    }

    @Test
    void incrementsPublishedViewCountAtomically() {
        Problem problem = saveProblem(ProblemStatus.PUBLISHED);

        assertEquals(
                1,
                problemRepository.incrementPublicViewCount(problem.getId())
        );
        entityManager.clear();

        Problem updated = problemRepository.findById(problem.getId())
                .orElseThrow();
        assertEquals(1L, updated.getViewCount());
    }

    private Problem saveProblem(ProblemStatus status) {
        return problemRepository.saveAndFlush(Problem.builder()
                .authorId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .title("A valid repository test title")
                .description("A valid repository test description")
                .status(status)
                .build());
    }
}
