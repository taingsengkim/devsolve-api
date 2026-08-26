package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.RelatedProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suggestion query runs on {@code pg_trgm}, which the H2 datasource the
 * other problem repository tests use does not have — {@code similarity()} and
 * the {@code %} operators simply do not exist there. So this one needs real
 * PostgreSQL.
 *
 * <p>Note that schema.sql runs before Hibernate creates the tables, so its
 * {@code idx_problems_*_trgm} blocks are skipped here and these queries plan
 * as sequential scans. That is fine: what is under test is the ranking, not
 * the access path. The {@code CREATE EXTENSION} at the top of that file is
 * unguarded, which is why the operators resolve at all.
 *
 * <p>Every test is transactional so its fixtures roll back; without that the
 * container database carries rows from one test into the next and the
 * ordering assertions start matching each other's data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class ProblemRelatedRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemService problemService;

    @Test
    @Transactional
    void rankingSurvivesATypoThatSubstringSearchWouldMiss() {
        Problem match = save(
                ProblemStatus.PUBLISHED,
                "Flyway migration checksum mismatch on redeploy",
                "The checksum recorded for V3 does not match the file."
        );
        save(
                ProblemStatus.PUBLISHED,
                "How do I center a div",
                "Flexbox refuses to cooperate with my layout."
        );

        // "migraton" is the point: LIKE '%flyway migraton%' finds nothing,
        // which is exactly the case a suggestion panel has to handle.
        List<RelatedProblemProjection> result = findRelated(
                "flyway migraton checksum mismatch"
        );

        assertEquals(1, result.size());
        assertEquals(match.getId(), result.getFirst().getId());
    }

    @Test
    @Transactional
    void aShortTitleStillMatchesTheBodyOfALongDescription() {
        Problem match = save(
                ProblemStatus.PUBLISHED,
                "Deployment keeps dying overnight",
                "We noticed the container exits with code 137 shortly after "
                        + "the nightly job starts, and the logs stop without "
                        + "anything that looks like an error."
        );

        // No overlap with the title at all — this only comes back if the
        // word_similarity predicate against the description is doing its job.
        List<RelatedProblemProjection> result = findRelated(
                "container exits with code 137"
        );

        assertEquals(1, result.size());
        assertEquals(match.getId(), result.getFirst().getId());
    }

    @Test
    @Transactional
    void solvedProblemsOutrankABetterMatchThatIsStillOpen() {
        Problem solved = save(
                ProblemStatus.RESOLVED,
                "Docker build fails on Apple Silicon",
                "The build dies partway through on an M-series machine."
        );
        Problem open = save(
                ProblemStatus.PUBLISHED,
                "Docker build fails on Apple Silicon M1",
                "The build dies partway through on an M-series machine."
        );

        // The open one is the closer string match; the solved one still leads,
        // because somebody about to ask a question wants the answer.
        List<RelatedProblemProjection> result = findRelated(
                "docker build fails on apple silicon m1"
        );

        assertEquals(2, result.size());
        assertEquals(solved.getId(), result.getFirst().getId());
        assertEquals(open.getId(), result.get(1).getId());
    }

    @Test
    @Transactional
    void unpublishedProblemsAreNeverSuggested() {
        save(
                ProblemStatus.DRAFT,
                "Flyway migration checksum mismatch on redeploy",
                "Still writing this one up."
        );
        save(
                ProblemStatus.PENDING_APPROVAL,
                "Flyway migration checksum mismatch again",
                "Waiting on a moderator."
        );

        assertTrue(findRelated("flyway migration checksum mismatch").isEmpty());
    }

    @Test
    @Transactional
    void theProblemBeingEditedIsNotOfferedAsItsOwnSuggestion() {
        Problem editing = save(
                ProblemStatus.PUBLISHED,
                "Flyway migration checksum mismatch on redeploy",
                "The checksum recorded for V3 does not match the file."
        );

        assertFalse(
                problemRepository.findRelated(
                        "flyway migration checksum mismatch",
                        null,
                        5
                ).isEmpty(),
                "fixture must match, or the exclusion proves nothing"
        );
        assertTrue(problemRepository.findRelated(
                "flyway migration checksum mismatch",
                editing.getId(),
                5
        ).isEmpty());
    }

    @Test
    @Transactional
    void aQueryTooShortToRankOnIsAnsweredWithNothing() {
        save(
                ProblemStatus.PUBLISHED,
                "Flyway migration checksum mismatch on redeploy",
                "The checksum recorded for V3 does not match the file."
        );

        assertTrue(
                problemService.findRelated("fly", null, 5).isEmpty(),
                "three characters is a couple of trigrams and matches "
                        + "far too much to be worth showing"
        );
        assertFalse(
                problemService.findRelated(
                        "Flyway Migration Checksum",
                        null,
                        5
                ).isEmpty(),
                "the service has to lowercase, since the query lowercases "
                        + "the column side"
        );
    }

    @Test
    @Transactional
    void solvedFlagAndCountersRideAlongWithTheSuggestion() {
        Problem solved = save(
                ProblemStatus.RESOLVED,
                "Flyway migration checksum mismatch on redeploy",
                "The checksum recorded for V3 does not match the file."
        );

        List<RelatedProblemResponse> result = problemService.findRelated(
                "flyway migration checksum mismatch",
                null,
                5
        );

        assertEquals(1, result.size());
        RelatedProblemResponse suggestion = result.getFirst();
        assertEquals(solved.getId(), suggestion.id());
        assertEquals(ProblemStatus.RESOLVED, suggestion.status());
        assertTrue(suggestion.solved());
        assertEquals(0L, suggestion.solutionCount());
    }

    private List<RelatedProblemProjection> findRelated(String query) {
        return problemRepository.findRelated(query, null, 5);
    }

    private Problem save(
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
