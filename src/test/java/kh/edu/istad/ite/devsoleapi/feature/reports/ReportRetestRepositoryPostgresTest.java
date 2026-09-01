package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The retest row against a real Postgres.
 *
 * <p>Three things here exist only in the database and cannot fail in a mocked
 * test: {@code retest_verdict_enum} and {@code report_environment_enum} are
 * named enum types Hibernate binds as untyped parameters, {@code attachment_ids}
 * is a jsonb column mapped to a {@code List<UUID>}, and {@code report_state_enum}
 * has to have gained its {@code retesting} value from schema.sql before a
 * retested report can be stored at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class ReportRetestRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ReportRetestRepository reportRetestRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void aCompletedRetestRoundTripsThroughPostgres() {
        Report report = persistedReport();
        UserProfile requester = persistedProfile();
        UUID evidence = UUID.randomUUID();

        ReportRetest retest = reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(1)
                        .environment(ReportEnvironment.STAGING)
                        .targetEndpoint("https://staging.example.test/v1/x")
                        .requestNotes("Fix deployed in v2.4.1.")
                        .bountyReward(new BigDecimal("50.00"))
                        .requestedBy(requester)
                        .build()
        );

        retest.setVerdict(RetestVerdict.VERIFIED_FIXED);
        retest.setResultNotes("Now returns 403.");
        retest.setAttachmentIds(List.of(evidence));
        retest.setCompletedBy(report.getReporter());
        retest.setCompletedAt(LocalDateTime.now());
        reportRetestRepository.saveAndFlush(retest);
        entityManager.clear();

        ReportRetest reloaded = reportRetestRepository
                .findById(retest.getId())
                .orElseThrow();
        assertEquals(1, reloaded.getAttemptNumber());
        assertEquals(ReportEnvironment.STAGING, reloaded.getEnvironment());
        assertEquals(RetestVerdict.VERIFIED_FIXED, reloaded.getVerdict());
        assertEquals(
                new BigDecimal("50.00"),
                reloaded.getBountyReward()
        );
        assertEquals(List.of(evidence), reloaded.getAttachmentIds());
        assertEquals(
                report.getReporter().getId(),
                reloaded.getCompletedBy().getId()
        );
    }

    /**
     * The value schema.sql adds to an enum type that already exists on every
     * deployed database. Without that ALTER a retested report cannot be saved
     * at all, and this is the only place that would notice.
     */
    @Test
    @Transactional
    void aReportCanBeStoredInTheRetestingState() {
        Report report = persistedReport();
        report.setState(ReportState.RETESTING);
        reportRepository.saveAndFlush(report);
        entityManager.clear();

        assertEquals(
                ReportState.RETESTING,
                reportRepository.findById(report.getId())
                        .orElseThrow()
                        .getState()
        );
    }

    @Test
    @Transactional
    void theOpenAttemptIsTheOneWithNoCompletionAndAttemptsClimb() {
        Report report = persistedReport();
        UserProfile requester = persistedProfile();

        ReportRetest first = reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(1)
                        .requestedBy(requester)
                        .verdict(RetestVerdict.STILL_VULNERABLE)
                        .completedBy(report.getReporter())
                        .completedAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
        ReportRetest second = reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(2)
                        .requestedBy(requester)
                        .build()
        );
        entityManager.clear();

        assertEquals(
                2,
                reportRetestRepository
                        .findHighestAttemptNumber(report.getId())
        );

        ReportRetest open = reportRetestRepository
                .findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                        report.getId()
                )
                .orElseThrow();
        assertEquals(second.getId(), open.getId());
        assertTrue(open.isOpen());
        assertNull(open.getVerdict());
        assertEquals(1, first.getAttemptNumber());
    }

    /**
     * The four states the expiry sweep has to tell apart. The one that matters
     * most is the attempt with no {@code due_at}: those are the rows written
     * before there was a window, and expiring them would lapse every retest
     * outstanding on the deploy that first ran the sweep.
     */
    @Test
    @Transactional
    void onlyOpenAttemptsPastTheirDeadlineAreOverdue() {
        Report report = persistedReport();
        UserProfile requester = persistedProfile();
        LocalDateTime now = LocalDateTime.now();

        ReportRetest overdue = reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(1)
                        .requestedBy(requester)
                        .dueAt(now.minusDays(1))
                        .build()
        );
        reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(2)
                        .requestedBy(requester)
                        .dueAt(now.plusDays(7))
                        .build()
        );
        reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(3)
                        .requestedBy(requester)
                        .dueAt(now.minusDays(3))
                        .verdict(RetestVerdict.VERIFIED_FIXED)
                        .completedBy(report.getReporter())
                        .completedAt(now.minusDays(4))
                        .build()
        );
        reportRetestRepository.saveAndFlush(
                ReportRetest.builder()
                        .report(report)
                        .attemptNumber(4)
                        .requestedBy(requester)
                        .build()
        );
        entityManager.clear();

        assertEquals(
                List.of(overdue.getId()),
                reportRetestRepository.findOverdueIds(now)
        );
    }

    @Test
    @Transactional
    void aReportWithNoRetestHasNoAttemptNumberYet() {
        assertNull(
                reportRetestRepository
                        .findHighestAttemptNumber(persistedReport().getId())
        );
    }

    private Report persistedReport() {
        UserProfile reporter = persistedProfile();
        Program program = programRepository.saveAndFlush(
                Program.builder()
                        .organizationId(UUID.randomUUID())
                        .name("Gateway")
                        .handle("gateway-" + UUID.randomUUID())
                        .build()
        );
        return reportRepository.saveAndFlush(
                Report.builder()
                        .program(program)
                        .reporter(reporter)
                        .title("Broken access control")
                        .vulnerabilityInformation("A user reads another.")
                        .reportedSeverity(Severity.HIGH)
                        .severity(Severity.HIGH)
                        .state(ReportState.VALID_CONFIRMED)
                        .build()
        );
    }

    private UserProfile persistedProfile() {
        String handle = "user" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(handle + "@example.test");
        profile.setUsername(handle);
        profile.setFullName("Test Person");
        profile.setStatus(UserStatus.ACTIVE);
        return userProfileRepository.saveAndFlush(profile);
    }
}
