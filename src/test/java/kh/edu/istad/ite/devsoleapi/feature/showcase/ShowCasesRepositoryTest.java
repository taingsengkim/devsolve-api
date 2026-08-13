package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * These assert almost nothing about results and that is the point.
 *
 * <p>The showcase listing queries carry three things a mock cannot check: an
 * entity graph naming attributes by string, an EXISTS over a second entity,
 * and — on the scored variant — a scalar subquery inside ORDER BY. Any of
 * those can be wrong in a way that compiles perfectly and then fails the first
 * time Hibernate tries to translate it, which on a public feed means the front
 * page. Running each query once against a real engine is what catches that.
 *
 * <p>The rows themselves are left to the service tests: showcases need an
 * author and a category, and both entities map Postgres enum types that are
 * not worth reproducing here to prove a query parses.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ShowCasesRepositoryTest {

    @Autowired
    private ShowCasesRepository showCasesRepository;

    @Test
    void theListingQueryTranslatesWithEveryFilterAbsent() {
        assertEquals(0, assertDoesNotThrow(() ->
                showCasesRepository.searchPublished(
                        ReviewStatus.APPROVED,
                        null,
                        null,
                        null,
                        PageRequest.of(0, 20, Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        ))
                )
        ).getTotalElements());
    }

    @Test
    void theListingQueryTranslatesWithEveryFilterPresent() {
        assertEquals(0, assertDoesNotThrow(() ->
                showCasesRepository.searchPublished(
                        ReviewStatus.APPROVED,
                        "%anything%",
                        java.util.UUID.randomUUID(),
                        "spring-boot",
                        PageRequest.of(0, 20, Sort.by(
                                Sort.Direction.DESC,
                                "viewCount"
                        ).and(Sort.by(Sort.Direction.DESC, "id")))
                )
        ).getTotalElements());
    }

    @Test
    void theScoredListingTranslatesForTopAndForTrending() {
        assertDoesNotThrow(() -> showCasesRepository.searchPublishedByScore(
                ReviewStatus.APPROVED,
                null,
                null,
                null,
                null,
                VoteType.SHOWCASE,
                PageRequest.of(0, 20)
        ));

        assertDoesNotThrow(() -> showCasesRepository.searchPublishedByScore(
                ReviewStatus.APPROVED,
                "%anything%",
                java.util.UUID.randomUUID(),
                "spring-boot",
                LocalDateTime.now().minusDays(30),
                VoteType.SHOWCASE,
                PageRequest.of(0, 20)
        ));
    }
}
