package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.WeaknessRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six analytics aggregates against a real Postgres.
 *
 * <p>Every one of them is native SQL, so nothing about them can fail in a
 * mocked test: {@code date_trunc}, aggregate {@code FILTER}, interval
 * arithmetic and the enum-to-text casts all only exist in the database, and a
 * quoted alias that stops matching its projection getter fails at runtime with
 * no compiler to catch it.
 *
 * <p>The scope clause gets the closest look. Reports reach an organization
 * through their program, and a clause that let one company's findings into
 * another's dashboard would be a data leak that reads like a working feature.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class OrganizationAnalyticsRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    private static final int TRIAGE_TARGET_HOURS = 24;

    @Autowired
    private OrganizationAnalyticsRepository analyticsRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramAssetRepository programAssetRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ReportRewardRepository reportRewardRepository;
    @Autowired
    private WeaknessRepository weaknessRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private EntityManager entityManager;

    /**
     * The guarantee the dashboard is built on: an organization nobody has ever
     * reported against still gets a row of zeros rather than a null projection
     * the caller would have to guard.
     */
    @Test
    @Transactional
    void anOrganizationWithNoReportsGetsARowOfZeros() {
        LocalDateTime now = LocalDateTime.now();
        OrganizationAnalyticsRepository.WindowSummary summary =
                analyticsRepository.summarize(
                        UUID.randomUUID(),
                        null,
                        now.minusMonths(6),
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                );

        assertNotNull(summary);
        assertEquals(0L, summary.getTotalReports());
        assertEquals(0L, summary.getAcceptedReports());
        assertEquals(0L, summary.getActiveResearchers());
        assertEquals(0, summary.getBountiesPaid().signum());
        assertEquals(0L, summary.getReputationPoints());
        assertEquals(0.0, summary.getMeanTriageHours());
        assertEquals(0.0, summary.getMeanResolveDays());
        assertEquals(0L, summary.getSlaDecidedReports());
        assertNull(summary.getFirstSubmittedOn());
    }

    @Test
    @Transactional
    void everyBreakdownIsEmptyForAnOrganizationWithNoReports() {
        LocalDateTime now = LocalDateTime.now();
        UUID organizationId = UUID.randomUUID();

        assertEquals(List.of(), analyticsRepository.findSubmissionTrend(
                organizationId, null, now.minusMonths(6), now, "month"
        ));
        assertEquals(List.of(), analyticsRepository.findSeverityDistribution(
                organizationId, null, now.minusMonths(6), now
        ));
        assertEquals(List.of(), analyticsRepository.findTopWeaknesses(
                organizationId, null, now.minusMonths(6), now, 10
        ));
        assertEquals(List.of(), analyticsRepository.findTopAssets(
                organizationId, null, now.minusMonths(6), now, 10
        ));
        assertEquals(List.of(), analyticsRepository.findTopResearchers(
                organizationId, null, now.minusMonths(6), now, 10
        ));
    }

    @Test
    @Transactional
    void theHeadlineFiguresAddUpOverTheWindow() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        OrganizationAnalyticsRepository.WindowSummary summary =
                analyticsRepository.summarize(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                );

        assertEquals(3L, summary.getTotalReports());
        assertEquals(1L, summary.getAcceptedReports());
        assertEquals(1L, summary.getRejectedReports());
        assertEquals(1L, summary.getResolvedReports());
        assertEquals(2L, summary.getActiveResearchers());
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(summary.getBountiesPaid())
        );
        assertEquals(40L, summary.getReputationPoints());
        assertEquals(5.0, summary.getMeanTriageHours(), 0.01);
        assertEquals(3.0, summary.getMeanResolveDays(), 0.01);
        // The one triaged report was in time; the two still untriaged are both
        // past the target, so all three have been settled one way or another.
        assertEquals(3L, summary.getSlaDecidedReports());
        assertEquals(1L, summary.getSlaMetReports());
        assertEquals(
                now.minusDays(40).toLocalDate().toString(),
                summary.getFirstSubmittedOn()
        );
    }

    /**
     * The scope clause. The other organization's report is worth ten times
     * every bounty in this fixture, so it cannot hide in a rounding error.
     */
    @Test
    @Transactional
    void anotherOrganizationsReportsAreNowhereInTheFigures() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        OrganizationAnalyticsRepository.WindowSummary summary =
                analyticsRepository.summarize(
                        fixture.otherOrganizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                );

        assertEquals(1L, summary.getTotalReports());
        assertEquals(
                0,
                new BigDecimal("9999.00").compareTo(summary.getBountiesPaid())
        );
    }

    @Test
    @Transactional
    void aProgramFilterNarrowsToThatProgramAlone() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        assertEquals(
                2L,
                analyticsRepository.summarize(
                        fixture.organizationId(),
                        fixture.firstProgramId(),
                        now.minusDays(90),
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                ).getTotalReports()
        );
    }

    /**
     * A window that ends before the reports do. The two boundaries are what
     * every "vs previous period" comparison rests on.
     */
    @Test
    @Transactional
    void theWindowExcludesWhatFallsOutsideIt() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        // Only the report from two days ago.
        assertEquals(
                1L,
                analyticsRepository.summarize(
                        fixture.organizationId(),
                        null,
                        now.minusDays(5),
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                ).getTotalReports()
        );
        // Only the report from forty days ago.
        assertEquals(
                1L,
                analyticsRepository.summarize(
                        fixture.organizationId(),
                        null,
                        now.minusDays(50),
                        now.minusDays(20),
                        now,
                        TRIAGE_TARGET_HOURS
                ).getTotalReports()
        );
    }

    @Test
    @Transactional
    void theTrendBucketsByMonthAndCarriesItsBounties() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.TrendBucket> buckets =
                analyticsRepository.findSubmissionTrend(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        "month"
                );

        assertEquals(
                3L,
                buckets.stream()
                        .mapToLong(
                                OrganizationAnalyticsRepository.TrendBucket
                                        ::getSubmitted
                        )
                        .sum()
        );
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        buckets.stream()
                                .map(OrganizationAnalyticsRepository.TrendBucket
                                        ::getBountyPaid)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )
        );
        // Every bucket start is a first-of-month, which is what the zero-fill
        // in the service walks between.
        assertTrue(buckets.stream().allMatch(bucket ->
                bucket.getBucketStart().endsWith("-01")
        ));
    }

    /** Daily buckets are the other {@code date_trunc} unit in use. */
    @Test
    @Transactional
    void theTrendAlsoBucketsByDay() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.TrendBucket> buckets =
                analyticsRepository.findSubmissionTrend(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        "day"
                );

        assertEquals(3, buckets.size());
        assertEquals(
                now.minusDays(40).toLocalDate().toString(),
                buckets.getFirst().getBucketStart()
        );
    }

    /**
     * The severity a report is counted under falls back through settled,
     * triaged and claimed — so an untriaged report still lands in a band
     * rather than disappearing from the chart.
     */
    @Test
    @Transactional
    void severitiesFallBackToWhatTheReporterClaimed() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.SeverityTally> tallies =
                analyticsRepository.findSeverityDistribution(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now
                );

        assertEquals(3, tallies.size());
        assertEquals(
                1L,
                totalFor(tallies, "critical")
        );
        // Rejected, never triaged: counted under the severity its reporter
        // claimed.
        assertEquals(1L, totalFor(tallies, "low"));
        // Brand new, nobody has looked at it: still on the chart.
        assertEquals(1L, totalFor(tallies, "medium"));
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        tallies.stream()
                                .filter(tally ->
                                        "critical".equals(tally.getSeverity()))
                                .findFirst()
                                .orElseThrow()
                                .getAverageBounty()
                )
        );
    }

    @Test
    @Transactional
    void weaknessesAreCountedAgainstTheCatalogEntry() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.WeaknessTally> tallies =
                analyticsRepository.findTopWeaknesses(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        10
                );

        assertEquals(1, tallies.size());
        assertEquals(fixture.cweId(), tallies.getFirst().getCweId());
        assertEquals("Improper Access Control", tallies.getFirst().getName());
        assertEquals(1L, tallies.getFirst().getTotal());
        assertEquals(1L, tallies.getFirst().getCriticalCount());
    }

    @Test
    @Transactional
    void assetsCarryTheirTypeAsTextAndTheirOwnPayouts() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.AssetTally> tallies =
                analyticsRepository.findTopAssets(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        10
                );

        assertEquals(1, tallies.size());
        OrganizationAnalyticsRepository.AssetTally asset = tallies.getFirst();
        assertEquals("api.example.test", asset.getAssetTarget());
        // Lower-case database text, rebuilt into the enum by the caller.
        assertEquals("api", asset.getAssetType());
        assertEquals(1L, asset.getTotalReports());
        assertEquals(1L, asset.getCriticalCount());
        assertEquals(0L, asset.getHighCount());
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(asset.getTotalBounty())
        );
    }

    /**
     * Accepted findings only. A researcher whose report was rejected, and one
     * whose report nobody has triaged yet, are not partners the company owes
     * anything to yet.
     */
    @Test
    @Transactional
    void theLeaderboardHoldsOnlyResearchersWithAcceptedFindings() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<OrganizationAnalyticsRepository.ResearcherTally> tallies =
                analyticsRepository.findTopResearchers(
                        fixture.organizationId(),
                        null,
                        now.minusDays(90),
                        now,
                        10
                );

        assertEquals(1, tallies.size());
        OrganizationAnalyticsRepository.ResearcherTally top = tallies.getFirst();
        assertEquals(fixture.reporterId(), top.getUserId());
        assertEquals(1L, top.getValidReports());
        assertEquals(1L, top.getCriticalReports());
        assertEquals(40L, top.getReputationEarned());
        assertEquals(
                0,
                new BigDecimal("1000.00")
                        .compareTo(top.getTotalBountiesEarned())
        );
    }

    private long totalFor(
            List<OrganizationAnalyticsRepository.SeverityTally> tallies,
            String severity
    ) {
        return tallies.stream()
                .filter(tally -> severity.equals(tally.getSeverity()))
                .mapToLong(OrganizationAnalyticsRepository.SeverityTally::getTotal)
                .sum();
    }

    /**
     * One organization with two programs and three reports, plus a second
     * organization whose single well-paid report must never turn up in any of
     * the figures above.
     */
    private Fixture seed() {
        LocalDateTime now = LocalDateTime.now();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();

        Program first = persistedProgram(organizationId);
        Program second = persistedProgram(organizationId);
        Program elsewhere = persistedProgram(otherOrganizationId);

        ProgramAsset api = programAssetRepository.saveAndFlush(
                ProgramAsset.builder()
                        .program(first)
                        .assetType(AssetType.API)
                        .identifier("api.example.test")
                        .build()
        );
        String cweId = "CWE-T" + (int) (Math.random() * 8_999 + 1_000);
        Weakness weakness = weaknessRepository.saveAndFlush(
                Weakness.builder()
                        .cweId(cweId)
                        .name("Improper Access Control")
                        .build()
        );

        UserProfile reporter = persistedProfile();
        UserProfile otherReporter = persistedProfile();
        UserProfile triager = persistedProfile();

        LocalDateTime resolvedSubmission = now.minusDays(10);
        Report resolved = reportRepository.saveAndFlush(
                Report.builder()
                        .program(first)
                        .reporter(reporter)
                        .asset(api)
                        .weakness(weakness)
                        .title("Broken access control")
                        .vulnerabilityInformation("A user reads another.")
                        .reportedSeverity(Severity.CRITICAL)
                        .triageSeverity(Severity.CRITICAL)
                        .severity(Severity.CRITICAL)
                        .state(ReportState.RESOLVED)
                        .triagedBy(triager)
                        .triagedAt(resolvedSubmission.plusHours(5))
                        .resolvedAt(resolvedSubmission.plusDays(3))
                        .reputationPoints(40)
                        .reputationAwardedAt(resolvedSubmission.plusDays(3))
                        .build()
        );
        reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(resolved)
                        .amount(new BigDecimal("1000.00"))
                        .awardedBy(triager)
                        .build()
        );

        Report rejected = reportRepository.saveAndFlush(
                Report.builder()
                        .program(first)
                        .reporter(otherReporter)
                        .title("Missing rate limit")
                        .vulnerabilityInformation("No throttle on login.")
                        .reportedSeverity(Severity.LOW)
                        .state(ReportState.REJECTED)
                        .build()
        );

        Report untriaged = reportRepository.saveAndFlush(
                Report.builder()
                        .program(second)
                        .reporter(reporter)
                        .title("Reflected XSS")
                        .vulnerabilityInformation("A parameter is echoed.")
                        .reportedSeverity(Severity.MEDIUM)
                        .state(ReportState.NEW)
                        .build()
        );

        Report otherCompanys = reportRepository.saveAndFlush(
                Report.builder()
                        .program(elsewhere)
                        .reporter(reporter)
                        .title("Someone else's problem")
                        .vulnerabilityInformation("Not this dashboard's.")
                        .reportedSeverity(Severity.CRITICAL)
                        .severity(Severity.CRITICAL)
                        .state(ReportState.RESOLVED)
                        .build()
        );
        reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(otherCompanys)
                        .amount(new BigDecimal("9999.00"))
                        .awardedBy(triager)
                        .build()
        );

        // submitted_at is stamped by @CreationTimestamp and is not updatable,
        // so the only way to put a report in the past is to go around the
        // entity. Every window assertion above depends on these.
        backdate(resolved.getId(), resolvedSubmission);
        backdate(rejected.getId(), now.minusDays(40));
        backdate(untriaged.getId(), now.minusDays(2));
        backdate(otherCompanys.getId(), now.minusDays(1));

        return new Fixture(
                now,
                organizationId,
                otherOrganizationId,
                first.getId(),
                reporter.getId(),
                cweId
        );
    }

    private void backdate(UUID reportId, LocalDateTime submittedAt) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE public.reports
                        SET submitted_at = :submittedAt
                        WHERE id = :id
                        """)
                .setParameter("submittedAt", submittedAt)
                .setParameter("id", reportId)
                .executeUpdate();
    }

    private Program persistedProgram(UUID organizationId) {
        return programRepository.saveAndFlush(
                Program.builder()
                        .organizationId(organizationId)
                        .name("Gateway")
                        .handle("gateway-" + UUID.randomUUID())
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

    private record Fixture(
            LocalDateTime now,
            UUID organizationId,
            UUID otherOrganizationId,
            UUID firstProgramId,
            UUID reporterId,
            String cweId
    ) {
    }
}
