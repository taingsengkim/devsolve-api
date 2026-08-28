package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStep;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ShowCasesService#hardDelete} against real PostgreSQL.
 *
 * <p>The behaviour at stake is a foreign key, so H2 will not do: it silently
 * skips the DDL for entities that use PostgreSQL named enums, leaving part of
 * the schema — and its constraints — absent.
 *
 * <p>Deliberately not {@code @Transactional}: the constraint is checked when
 * the service's own transaction commits, which a surrounding rollback would
 * hide.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        // "create", not "create-drop": the container is discarded with the
        // class, and dropping afterwards races its shutdown.
        "spring.jpa.hibernate.ddl-auto=create"
})
class ShowCasesHardDeleteIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ShowCasesService showCasesService;

    @Autowired
    private ShowCasesRepository showCasesRepository;

    @Autowired
    private ShowCaseStepRepository showCaseStepRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @Autowired
    private ShowcaseRevisionWorkflow showcaseRevisionWorkflow;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hardDeleteRemovesTheShowcaseAndItsSteps() {
        UserProfile author = saveAuthor();
        ShowCases showcase = saveShowcase(author);
        saveStep(showcase, 1, "Model the domain");
        saveStep(showcase, 2, "Wire up the API");

        authenticate(author.getId());

        showCasesService.hardDelete(showcase.getId());

        assertTrue(
                showCasesRepository.findById(showcase.getId()).isEmpty()
        );
        assertTrue(
                showCaseStepRepository
                        .findByShowcase_IdOrderByStepNumberAsc(
                                showcase.getId()
                        )
                        .isEmpty()
        );
    }

    @Test
    void hardDeleteRemovesAnUnpublishedRevisionToo() {
        UserProfile author = saveAuthor();
        ShowCases showcase = saveShowcase(author);
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        showCasesRepository.saveAndFlush(showcase);
        saveStep(showcase, 1, "Model the domain");

        authenticate(author.getId());
        showcaseRevisionWorkflow.getOrCreate(showcase, author.getId());

        showCasesService.hardDelete(showcase.getId());

        assertTrue(
                showCasesRepository.findById(showcase.getId()).isEmpty()
        );
        assertTrue(
                showcaseRevisionRepository
                        .findByShowcase_Id(showcase.getId())
                        .isEmpty()
        );
    }

    private UserProfile saveAuthor() {
        UserProfile author = new UserProfile();
        author.setId(UUID.randomUUID());
        // Unique, and inside the 30 characters a username may occupy.
        author.setUsername("author" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16));
        author.setEmail("author-" + UUID.randomUUID() + "@example.test");
        author.setFullName("Integration Test Author");
        return userProfileRepository.saveAndFlush(author);
    }

    private ShowCases saveShowcase(UserProfile author) {
        ShowCases showcase = new ShowCases();
        showcase.setAuthor(author);
        showcase.setTitle("Integration test showcase");
        showcase.setOverview("Integration test overview");
        showcase.setReviewStatus(ReviewStatus.PENDING);
        showcase.setViewCount(0);
        return showCasesRepository.saveAndFlush(showcase);
    }

    private void saveStep(
            ShowCases showcase,
            int stepNumber,
            String title
    ) {
        ShowcaseStep step = new ShowcaseStep();
        step.setShowcase(showcase);
        step.setStepNumber(stepNumber);
        step.setTitle(title);
        showCaseStepRepository.saveAndFlush(step);
    }

    private void authenticate(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
