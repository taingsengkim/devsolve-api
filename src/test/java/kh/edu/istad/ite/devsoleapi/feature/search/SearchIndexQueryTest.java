package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five feeds the search indexes are built from, run once each.
 *
 * <p>Like {@code ShowCasesRepositoryTest}, these assert almost nothing about
 * results. What they are for is translation: every one of these queries
 * compares a {@code uuid} with {@code >} to page by keyset, the program one
 * orders by {@code greatest} over a left join, and the problem one is native
 * SQL against a schema-qualified table. Each of those is a thing that compiles
 * and then fails the first time Hibernate or the database is asked to make
 * sense of it — and the first time anything asks is a background sync pass,
 * where the failure is a log line nobody is reading.
 *
 * <p>The keyset's actual behaviour — that a cursor advances and never skips —
 * is {@code DocumentBatchTest} and {@code SearchIndexSynchronizerTest}, which
 * do not need a database to say it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SearchIndexQueryTest {

    /** Where a rebuild starts, as {@code SearchIndexSynchronizer} spells it. */
    private static final SyncCursor FROM_THE_BEGINNING =
            SyncCursor.startingAt(LocalDateTime.of(1970, 1, 1, 0, 0));

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ShowCasesRepository showCasesRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    void theProgramFeedTranslates() {
        assertTrue(assertDoesNotThrow(() ->
                programRepository.findChangedSince(
                        FROM_THE_BEGINNING.changedAt(),
                        FROM_THE_BEGINNING.id(),
                        PageRequest.of(0, 50)
                )
        ).isEmpty());
    }

    @Test
    void theShowcaseFeedTranslates() {
        assertTrue(assertDoesNotThrow(() ->
                showCasesRepository.findChangedSince(
                        FROM_THE_BEGINNING.changedAt(),
                        FROM_THE_BEGINNING.id(),
                        PageRequest.of(0, 50)
                )
        ).isEmpty());
    }

    @Test
    void theProblemFeedTranslates() {
        assertTrue(assertDoesNotThrow(() ->
                problemRepository.findChangedSince(
                        FROM_THE_BEGINNING.changedAt(),
                        FROM_THE_BEGINNING.id(),
                        PageRequest.of(0, 50)
                )
        ).isEmpty());
    }

    @Test
    void theOrganizationFeedTranslates() {
        assertTrue(assertDoesNotThrow(() ->
                organizationRepository.findChangedSince(
                        FROM_THE_BEGINNING.changedAt(),
                        FROM_THE_BEGINNING.id(),
                        PageRequest.of(0, 50)
                )
        ).isEmpty());
    }

    @Test
    void theUserFeedTranslates() {
        assertTrue(assertDoesNotThrow(() ->
                userProfileRepository.findChangedSince(
                        FROM_THE_BEGINNING.changedAt(),
                        FROM_THE_BEGINNING.id(),
                        PageRequest.of(0, 50)
                )
        ).isEmpty());
    }
}
