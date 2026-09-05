package kh.edu.istad.ite.devsoleapi.feature.platformstats;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The home page aggregates, and the program-card one beside them, against a
 * real Postgres.
 *
 * <p>All five are native SQL, so nothing about them can fail in a mocked test:
 * {@code date_trunc}, {@code to_char}, aggregate {@code FILTER},
 * {@code EXTRACT(EPOCH …)} and the bare enum literals only exist in the
 * database, and a quoted alias that stops matching its projection getter fails
 * at runtime with no compiler to catch it.
 *
 * <p>What gets the closest look is which rows each figure admits. These numbers
 * are printed on a landing page as a claim about the platform, and a filter
 * that quietly let in a private program or a suspended account would read like
 * a working feature.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class PlatformStatsRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    private PlatformStatsRepository statsRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ReportRewardRepository reportRewardRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;
    @Autowired
    private EntityManager entityManager;

    /**
     * A platform with nothing on it yet. Every query has to come back empty
     * rather than with a row of nulls — the service reads emptiness as "no
     * history" and hides the charts on the strength of it.
     */
    @Test
    @Transactional
    void everyAggregateIsEmptyOnAnUnusedPlatform() {
        assertEquals(List.of(), statsRepository.disbursedByMonth());
        assertEquals(List.of(), statsRepository.researchersByMonth());
        assertEquals(List.of(), statsRepository.liveProgramsByMonth());
        assertEquals(List.of(), statsRepository.validatedReportsByMonth());
    }

    @Test
    @Transactional
    void bountiesAreSummedIntoTheMonthTheyWereAwarded() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<PlatformStatsRepository.MonetaryPoint> points =
                statsRepository.disbursedByMonth();

        // 1000 + 250 last month, 500 this one. Summed rather than read
        // positionally: two of the three could land in the same bucket
        // depending on when in the month the suite runs.
        assertEquals(
                0,
                new BigDecimal("1750.00").compareTo(
                        points.stream()
                                .map(PlatformStatsRepository.MonetaryPoint
                                        ::getDelta)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )
        );
        assertEquals(
                0,
                new BigDecimal("1250.00").compareTo(
                        deltaFor(points, month(now.minusMonths(1)))
                )
        );
        assertTrue(points.stream().allMatch(point ->
                point.getBucket().length() == "yyyy-MM".length()
        ));
    }

    /**
     * A points-only award carries a null amount, and {@code SUM} over nothing
     * but nulls is null. A month with only those in it has to reach the caller
     * as zero — a null delta would break the running total the whole series is
     * built by adding up.
     */
    @Test
    @Transactional
    void aMonthOfPointsOnlyAwardsComesBackAsZeroRatherThanNull() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();
        ReportReward pointsOnly = reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(entityManager.getReference(
                                Report.class,
                                fixture.resolvedReportId()
                        ))
                        .points(20)
                        .awardedBy(entityManager.getReference(
                                UserProfile.class,
                                fixture.triagerId()
                        ))
                        .build()
        );
        // Its own month, so nothing else can fill the sum in for it.
        entityManager.createNativeQuery("""
                        UPDATE public.report_rewards
                        SET awarded_at = :awardedAt
                        WHERE id = :id
                        """)
                .setParameter("awardedAt", now.minusMonths(5))
                .setParameter("id", pointsOnly.getId())
                .executeUpdate();
        entityManager.clear();

        List<PlatformStatsRepository.MonetaryPoint> points =
                statsRepository.disbursedByMonth();

        PlatformStatsRepository.MonetaryPoint quietMonth = points.stream()
                .filter(point -> month(now.minusMonths(5))
                        .equals(point.getBucket()))
                .findFirst()
                .orElseThrow();
        assertNotNull(quietMonth.getDelta());
        assertEquals(0, quietMonth.getDelta().signum());
        // And the paid months are untouched by it.
        assertEquals(
                0,
                new BigDecimal("1750.00").compareTo(
                        points.stream()
                                .map(PlatformStatsRepository.MonetaryPoint
                                        ::getDelta)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                )
        );
    }

    /**
     * One researcher, counted once, in the month they first submitted — not
     * once per report and not in the month of their latest.
     */
    @Test
    @Transactional
    void aResearcherIsCountedOnceInTheMonthTheyArrived() {
        Fixture fixture = seed();
        LocalDateTime now = fixture.now();

        List<PlatformStatsRepository.CountPoint> points =
                statsRepository.researchersByMonth();

        // The suspended account and the one that has never submitted are both
        // out; the two who have are in, and the prolific one only once.
        assertEquals(2L, totalOf(points));
        assertEquals(1L, countFor(points, month(now.minusMonths(2))));
    }

    @Test
    @Transactional
    void aSuspendedAccountIsNotAnActiveResearcher() {
        Fixture fixture = seed();
        long before = totalOf(statsRepository.researchersByMonth());

        entityManager.createNativeQuery("""
                        UPDATE public.user_profiles
                        SET status = 'suspended'
                        WHERE id = :id
                        """)
                .setParameter("id", fixture.reporterId())
                .executeUpdate();
        entityManager.clear();

        assertEquals(before - 1, totalOf(statsRepository.researchersByMonth()));
    }

    /**
     * The same filters the public listing applies. A private, unapproved,
     * paused, deleted or unverified-company program is not something a visitor
     * can open, so counting it would inflate the number against nothing they
     * could go and look at.
     */
    @Test
    @Transactional
    void onlyProgramsThePublicListingWouldReturnAreCountedAsLive() {
        Fixture fixture = seed();

        assertEquals(1L, totalOf(statsRepository.liveProgramsByMonth()));
        assertEquals(
                month(fixture.publishedAt()),
                statsRepository.liveProgramsByMonth().getFirst().getBucket()
        );
    }

    @Test
    @Transactional
    void confirmedFindingsAreBucketedByWhenTriageConfirmedThem() {
        seed();

        List<PlatformStatsRepository.CountPoint> points =
                statsRepository.validatedReportsByMonth();

        // Resolved and valid_confirmed count; rejected, duplicate and the one
        // nobody has triaged do not.
        assertEquals(2L, totalOf(points));
        // The two were triaged a month apart, so a query bucketing by anything
        // constant — the submission, or the clock — would fold them into one.
        assertEquals(2, points.size());
    }

    @Test
    @Transactional
    void programCardStatsCountSubmissionsResolutionsAndMeanTriageTime() {
        Fixture fixture = seed();

        List<ReportRepository.ProgramReportStats> stats =
                reportRepository.findStatsByProgramIds(
                        Set.of(fixture.liveProgramId())
                );

        assertEquals(1, stats.size());
        ReportRepository.ProgramReportStats card = stats.getFirst();
        assertEquals(fixture.liveProgramId(), card.getId());
        // Every report on the program, whatever its state.
        assertEquals(4L, card.getTotalSubmissions());
        assertEquals(1L, card.getResolvedReports());
        // Two triaged reports, at five hours and at three days. The untriaged
        // ones are skipped by AVG rather than averaged in as zero, which would
        // have dragged this to 0.8.
        assertEquals(1.6, card.getAverageTriageDays(), 0.01);
    }

    /**
     * Null, not zero. Zero would reach the card as "triaged the same day" on a
     * program that has never answered anybody.
     */
    @Test
    @Transactional
    void meanTriageTimeIsNullWhenNothingOnTheProgramHasBeenTriaged() {
        Fixture fixture = seed();

        List<ReportRepository.ProgramReportStats> stats =
                reportRepository.findStatsByProgramIds(
                        Set.of(fixture.untriagedProgramId())
                );

        assertEquals(1, stats.size());
        assertEquals(1L, stats.getFirst().getTotalSubmissions());
        assertEquals(0L, stats.getFirst().getResolvedReports());
        assertNull(stats.getFirst().getAverageTriageDays());
    }

    /** A program nobody has reported against is absent, not a row of zeros. */
    @Test
    @Transactional
    void aProgramWithNoReportsGetsNoRow() {
        seed();

        assertEquals(
                List.of(),
                reportRepository.findStatsByProgramIds(
                        Set.of(UUID.randomUUID())
                )
        );
    }

    private String month(LocalDateTime at) {
        return at.format(MONTH);
    }

    private long totalOf(List<PlatformStatsRepository.CountPoint> points) {
        return points.stream()
                .mapToLong(PlatformStatsRepository.CountPoint::getDelta)
                .sum();
    }

    private long countFor(
            List<PlatformStatsRepository.CountPoint> points,
            String bucket
    ) {
        return points.stream()
                .filter(point -> bucket.equals(point.getBucket()))
                .mapToLong(PlatformStatsRepository.CountPoint::getDelta)
                .sum();
    }

    private BigDecimal deltaFor(
            List<PlatformStatsRepository.MonetaryPoint> points,
            String bucket
    ) {
        return points.stream()
                .filter(point -> bucket.equals(point.getBucket()))
                .map(PlatformStatsRepository.MonetaryPoint::getDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * One live program with four reports on it, one program of every kind the
     * public listing hides, and a second live-looking program whose reports
     * have never been triaged.
     */
    private Fixture seed() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = now.minusMonths(3);

        UserProfile owner = persistedProfile();
        UserProfile reporter = persistedProfile();
        UserProfile occasional = persistedProfile();
        UserProfile triager = persistedProfile();
        // Registered, never submitted anything: not a researcher.
        persistedProfile();

        Organization active = persistedOrganization(
                owner,
                OrganizationStatus.ACTIVE
        );
        Organization pending = persistedOrganization(
                owner,
                OrganizationStatus.PENDING
        );

        Program live = persistedProgram(active, publishedAt, program -> { });
        // Everything the listing filters out, one program each.
        persistedProgram(active, publishedAt, program ->
                program.setVisibility(Visibility.PRIVATE));
        persistedProgram(active, publishedAt, program ->
                program.setState(ProgramState.PAUSED));
        persistedProgram(active, publishedAt, program ->
                program.setSubmissionState(SubmissionState.PENDING_REVIEW));
        persistedProgram(active, publishedAt, program ->
                program.setDeletedAt(now));
        persistedProgram(pending, publishedAt, program -> { });

        Report resolved = persistedReport(
                live,
                reporter,
                ReportState.RESOLVED,
                report -> {
                    report.setTriagedBy(triager);
                    report.setTriagedAt(now.minusMonths(2).plusHours(5));
                    report.setResolvedAt(now.minusMonths(2).plusDays(4));
                }
        );
        reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(resolved)
                        .amount(new BigDecimal("1000.00"))
                        .awardedBy(triager)
                        .build()
        );
        Report confirmed = persistedReport(
                live,
                occasional,
                ReportState.VALID_CONFIRMED,
                report -> {
                    report.setTriagedBy(triager);
                    report.setTriagedAt(now.minusMonths(1).plusDays(3));
                }
        );
        reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(confirmed)
                        .amount(new BigDecimal("250.00"))
                        .awardedBy(triager)
                        .build()
        );
        reportRewardRepository.saveAndFlush(
                ReportReward.builder()
                        .report(resolved)
                        .amount(new BigDecimal("500.00"))
                        .awardedBy(triager)
                        .build()
        );
        // Neither is a confirmed finding, and neither has a triage time.
        persistedReport(live, reporter, ReportState.REJECTED, report -> { });
        persistedReport(live, reporter, ReportState.DUPLICATE, report -> { });

        Program untriaged = persistedProgram(active, publishedAt, program ->
                program.setVisibility(Visibility.PRIVATE));
        persistedReport(untriaged, reporter, ReportState.NEW, report -> { });

        backdateReport(resolved.getId(), now.minusMonths(2));
        backdateReport(confirmed.getId(), now.minusMonths(1));
        backdateAward(resolved.getId(), now.minusMonths(1));
        backdateAward(confirmed.getId(), now.minusMonths(1));
        // The 500 correction on the resolved report lands this month, so the
        // two award months are distinguishable.
        entityManager.createNativeQuery("""
                        UPDATE public.report_rewards
                        SET awarded_at = :awardedAt
                        WHERE report_id = :id
                          AND amount = 500.00
                        """)
                .setParameter("awardedAt", now)
                .setParameter("id", resolved.getId())
                .executeUpdate();
        entityManager.clear();

        return new Fixture(
                now,
                publishedAt,
                live.getId(),
                untriaged.getId(),
                reporter.getId(),
                triager.getId(),
                resolved.getId()
        );
    }

    /**
     * {@code submitted_at} and {@code awarded_at} are stamped by
     * {@code @CreationTimestamp} and are not updatable, so the only way to put
     * either in the past is to go around the entity. Every month assertion
     * above depends on these.
     */
    private void backdateReport(UUID reportId, LocalDateTime submittedAt) {
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

    private void backdateAward(UUID reportId, LocalDateTime awardedAt) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        UPDATE public.report_rewards
                        SET awarded_at = :awardedAt
                        WHERE report_id = :id
                        """)
                .setParameter("awardedAt", awardedAt)
                .setParameter("id", reportId)
                .executeUpdate();
    }

    private Report persistedReport(
            Program program,
            UserProfile reporter,
            ReportState state,
            java.util.function.Consumer<Report> customize
    ) {
        Report report = Report.builder()
                .program(program)
                .reporter(reporter)
                .title("A finding")
                .vulnerabilityInformation("Something is reachable.")
                .reportedSeverity(Severity.HIGH)
                .state(state)
                .build();
        customize.accept(report);
        return reportRepository.saveAndFlush(report);
    }

    private Program persistedProgram(
            Organization organization,
            LocalDateTime publishedAt,
            java.util.function.Consumer<Program> customize
    ) {
        Program program = Program.builder()
                .organizationId(organization.getId())
                .name("Gateway")
                .handle("gateway-" + UUID.randomUUID())
                .state(ProgramState.ACTIVE)
                .submissionState(SubmissionState.APPROVED)
                .visibility(Visibility.PUBLIC)
                .publishedAt(publishedAt)
                .build();
        customize.accept(program);
        return programRepository.saveAndFlush(program);
    }

    private Organization persistedOrganization(
            UserProfile owner,
            OrganizationStatus status
    ) {
        Organization organization = new Organization();
        organization.setName("Acme");
        organization.setSlug("acme-" + UUID.randomUUID());
        organization.setOwnerJobTitle("CTO");
        organization.setCompanySize("11-50");
        organization.setCountry("Cambodia");
        organization.setJoiningReason("To find bugs before somebody else does");
        organization.setStatus(status);
        organization.setOwner(owner);
        return organizationRepository.saveAndFlush(organization);
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
            LocalDateTime publishedAt,
            UUID liveProgramId,
            UUID untriagedProgramId,
            UUID reporterId,
            UUID triagerId,
            UUID resolvedReportId
    ) {
    }
}
