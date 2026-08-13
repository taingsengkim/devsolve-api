package kh.edu.istad.ite.devsoleapi.feature.problem;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code replace = NONE} keeps the datasource this profile configures. Without
 * it {@code @DataJpaTest} swaps in its own embedded H2 with a generated URL,
 * which silently drops both {@code MODE=PostgreSQL} and the enum-type setup —
 * so the tests run against a different dialect than production and any table
 * using a named enum never gets created.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

        Page<Problem> result = listAll();

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
                null,
                null,
                false,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(published.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void searchesTitleAndDescriptionCaseInsensitively() {
        Problem matching = saveProblem(
                ProblemStatus.PUBLISHED,
                "Flyway Migration Keeps Failing",
                "The Checksum Does Not Match On Redeploy"
        );
        saveProblem(
                ProblemStatus.PUBLISHED,
                "Unrelated title",
                "Unrelated description"
        );

        // The caller lowercases the pattern; the query lowercases the column.
        // Between them a lowercase search finds text stored in any case,
        // which is the half of the contract that lives here.
        assertEquals(
                matching.getId(),
                search("%flyway%").getContent().getFirst().getId()
        );
        assertEquals(
                matching.getId(),
                search("%checksum%").getContent().getFirst().getId(),
                "description has to be searched too, not just the title"
        );
        assertEquals(0, search("%nothing matches this%").getTotalElements());
    }

    @Test
    void statusFilterNarrowsWithinThePublicStatuses() {
        saveProblem(ProblemStatus.PUBLISHED);
        Problem resolved = saveProblem(ProblemStatus.RESOLVED);

        Page<Problem> result = problemRepository.findPublished(
                null,
                null,
                null,
                null,
                null,
                ProblemStatus.RESOLVED,
                false,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(resolved.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void unansweredFilterKeepsProblemsWithNoPublishedSolution() {
        Problem published = saveProblem(ProblemStatus.PUBLISHED);

        Page<Problem> result = problemRepository.findPublished(
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals(published.getId(), result.getContent().getFirst().getId());
    }

    @Test
    void scoreOrderedListingRunsAndKeepsUnvotedProblems() {
        Problem published = saveProblem(ProblemStatus.PUBLISHED);

        Page<Problem> result = problemRepository.findPublishedByScore(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                VoteType.PROBLEM,
                PageRequest.of(0, 20)
        );

        assertEquals(
                1,
                result.getTotalElements(),
                "a problem nobody voted on scores zero rather than dropping "
                        + "out of the ordering"
        );
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

    private Page<Problem> listAll() {
        return problemRepository.findPublished(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                PageRequest.of(0, 20)
        );
    }

    private Page<Problem> search(String pattern) {
        return problemRepository.findPublished(
                null,
                null,
                null,
                null,
                pattern,
                null,
                false,
                PageRequest.of(0, 20)
        );
    }

    private Problem saveProblem(ProblemStatus status) {
        return saveProblem(
                status,
                "A valid repository test title",
                "A valid repository test description"
        );
    }

    private Problem saveProblem(
            ProblemStatus status,
            String title,
            String description
    ) {
        return problemRepository.saveAndFlush(Problem.builder()
                .authorId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .title(title)
                .description(description)
                .status(status)
                .publishedAt(Instant.now())
                .build());
    }
}
