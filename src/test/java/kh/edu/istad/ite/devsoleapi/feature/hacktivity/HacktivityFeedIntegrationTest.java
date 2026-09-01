package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityFilter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.recognition.Recognition;
import kh.edu.istad.ite.devsoleapi.feature.recognition.RecognitionRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.WeaknessRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feed, read the way a caller reads it.
 *
 * <p>Deliberately not {@code @Transactional}: the service opens its own
 * read-only transaction, and the associations it needs are loaded inside it.
 * A transaction wrapped around the test would keep the fixture's own session
 * open and hand the mapper entities that were already loaded, which passes
 * whether or not the query under test fetches anything — the exact failure
 * this suite exists to catch.
 */
@SpringBootTest
@ActiveProfiles("test")
class HacktivityFeedIntegrationTest {

    @Autowired
    private HacktivityService hacktivityService;

    @Autowired
    private HacktivityRepository hacktivityRepository;
    @Autowired
    private RecognitionRepository recognitionRepository;
    @Autowired
    private ReportRewardRepository reportRewardRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private WeaknessRepository weaknessRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserProfile dara;
    private UserProfile sokha;
    private Organization acme;
    private Program gateway;

    @BeforeEach
    void seed() {

        hacktivityRepository.deleteAll();
        reportRewardRepository.deleteAll();
        recognitionRepository.deleteAll();
        reportRepository.deleteAll();
        programRepository.deleteAll();
        organizationRepository.deleteAll();
        userProfileRepository.deleteAll();
        weaknessRepository.deleteAll();

        dara = userProfileRepository.save(
                profile("dara", "Sok Dara", 9240)
        );
        sokha = userProfileRepository.save(
                profile("sokha", "Chan Sokha", 120)
        );

        acme = organizationRepository.save(organization("Acme", "acme"));
        gateway = programRepository.save(
                program(acme.getId(), "Acme Web", "acme-web")
        );

        Weakness injection = weaknessRepository.save(
                weakness("CWE-94", "Code Injection")
        );

        // A paid critical, disclosed and classified: the fullest row a card
        // can be handed.
        Report rce = reportRepository.save(report(
                gateway, dara, "RCE in gateway", Severity.CRITICAL,
                DisclosureStatus.DISCLOSED, injection
        ));
        reportRewardRepository.save(reward(rce, new BigDecimal("20000.00"), 120));
        reportRewardRepository.save(reward(rce, new BigDecimal("500.00"), 5));

        // An unpaid, unclassified, undisclosed medium.
        Report xss = reportRepository.save(report(
                gateway, sokha, "Stored XSS in profile", Severity.MEDIUM,
                DisclosureStatus.NOT_DISCLOSED, null
        ));

        hacktivityRepository.save(entry(
                rce, dara, HacktivityEventType.BOUNTY_AWARDED,
                LocalDateTime.now().minusHours(1)
        ));
        hacktivityRepository.save(entry(
                xss, sokha, HacktivityEventType.RECOGNITION_AWARDED,
                LocalDateTime.now().minusHours(5)
        ));
    }

    // ------------------------------------------------------------ the P0

    /**
     * The endpoint answered 500 for every caller and every page shape — an
     * empty result set included, which is what said the failure was in the
     * query rather than in a row. Both halves are asserted here.
     */
    @Test
    void theFeedAnswersWithAPageRatherThanFailing() {

        Page<HacktivityResponse> feed = hacktivityService.search(
                filter(), PageRequest.of(0, 10)
        );

        assertEquals(2, feed.getTotalElements());
        assertEquals(2, feed.getContent().size());
    }

    @Test
    void aFilterThatMatchesNothingIsAnEmptyPageNotAnError() {

        Page<HacktivityResponse> feed = hacktivityService.search(
                new HacktivityFilter(
                        UUID.randomUUID(), null, null, null, null, null
                ),
                PageRequest.of(0, 10)
        );

        assertTrue(feed.getContent().isEmpty());
        assertEquals(0, feed.getTotalElements());
    }

    @Test
    void aPageBeyondTheEndIsEmptyRatherThanAnError() {

        assertTrue(hacktivityService.search(
                filter(), PageRequest.of(999, 10)
        ).getContent().isEmpty());
    }

    // ------------------------------------------- the fields a card needs

    @Test
    void aRowCarriesEverythingTheCardRenders() {

        HacktivityResponse row = newestFirst().getFirst();

        assertEquals(HacktivityEventType.BOUNTY_AWARDED, row.eventType());

        // The handle a profile URL is built from, not the display name.
        assertEquals("dara", row.user().username());
        assertEquals("Sok Dara", row.user().fullName());
        assertEquals(9240, row.user().reputation());

        assertEquals("acme", row.organization().slug());
        assertEquals("acme-web", row.program().handle());

        assertEquals(Severity.CRITICAL, row.report().severity());
        assertEquals(
                DisclosureStatus.DISCLOSED,
                row.report().disclosureStatus()
        );
        assertEquals("CWE-94", row.report().weakness().cweId());
        assertEquals("Code Injection", row.report().weakness().name());

        assertNotNull(row.createdAt());
    }

    /**
     * A report can be paid more than once and a card shows one number, so the
     * row carries the total rather than one of them.
     */
    @Test
    void everyPayoutOnAReportIsTotalledIntoOneAmount() {

        HacktivityResponse row = newestFirst().getFirst();

        assertEquals(
                0,
                new BigDecimal("20500.00").compareTo(row.reward().amount())
        );
        assertEquals(125, row.reward().points());
        assertEquals("USD", row.reward().currency());
    }

    @Test
    void anUnpaidRowHasNoRewardRatherThanAZeroOne() {

        HacktivityResponse row = newestFirst().get(1);

        assertNull(row.reward());
        assertNull(row.report().weakness());
    }

    // --------------------------------------------- rows without recognition

    /**
     * A resolution reaches the feed with no recognition behind it: the report
     * was fixed whether or not anybody was credited for it. The row still has
     * to hydrate — an inner join on the recognition would leave it in the page
     * and out of the fetch, which fails on the first association read.
     */
    @Test
    void aResolvedRowCarriesNoRecognitionButStillRenders() {

        Report fixed = reportRepository.save(report(
                gateway, dara, "Fixed already", Severity.LOW,
                DisclosureStatus.DISCLOSED, null
        ));
        hacktivityRepository.save(entryWithoutRecognition(
                fixed, dara, HacktivityEventType.REPORT_RESOLVED,
                LocalDateTime.now()
        ));

        HacktivityResponse row = newestFirst().getFirst();

        assertEquals(
                HacktivityEventType.REPORT_RESOLVED,
                row.eventType()
        );
        assertNull(row.recognition());

        // Everything else the card needs is still there.
        assertEquals("Fixed already", row.report().title());
        assertEquals(Severity.LOW, row.report().severity());
        assertEquals("dara", row.user().username());
        assertEquals("acme-web", row.program().handle());
    }

    /**
     * The windowed leaderboard scores what was actually paid, and reputation is
     * paid when a report is resolved -- so it reads the stamp on the report
     * rather than the feed. A feed entry is written on a best-effort basis and
     * swallows its own failures, and a report that was resolved and then
     * recognised has two rows: scoring off the feed would both miss points that
     * were paid and double points that were not.
     */
    @Test
    void theWindowScoresStampedResolutionsNotFeedRows() {

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long before = findingsScoredSince(since);

        // A feed row on its own is worth nothing.
        hacktivityRepository.save(entryWithoutRecognition(
                reportRepository.findAll().getFirst(),
                dara,
                HacktivityEventType.REPORT_RESOLVED,
                LocalDateTime.now()
        ));

        assertEquals(before, findingsScoredSince(since));

        // The stamp the resolution leaves is what scores.
        Report paid = report(
                gateway, dara, "Paid on resolution", Severity.HIGH,
                DisclosureStatus.DISCLOSED, null
        );
        paid.setReputationPoints(40);
        paid.setReputationAwardedAt(LocalDateTime.now());
        reportRepository.saveAndFlush(paid);

        assertEquals(before + 1, findingsScoredSince(since));
    }

    private long findingsScoredSince(LocalDateTime since) {
        return reportRepository.tallyReputationAwardedSince(since)
                .stream()
                .mapToLong(ReportRepository.SeverityTally::getFindings)
                .sum();
    }

    // ------------------------------------------------- disclosure privacy

    /**
     * The feed is anonymous, and a recognition is not a disclosure. Every row
     * carries the finding's severity and payout, but only a disclosed one may
     * carry the sentence that names the bug.
     */
    @Test
    void anUndisclosedRowWithholdsItsTitleButKeepsTheRestOfTheCard() {

        HacktivityResponse row = newestFirst().get(1);

        assertEquals(
                DisclosureStatus.NOT_DISCLOSED,
                row.report().disclosureStatus()
        );
        assertNull(row.report().title());

        // Withholding the title does not empty the card.
        assertNotNull(row.report().id());
        assertEquals(Severity.MEDIUM, row.report().severity());
        assertEquals("sokha", row.user().username());
        assertEquals("acme-web", row.program().handle());
    }

    @Test
    void aDisclosedRowStillCarriesItsTitle() {

        HacktivityResponse row = newestFirst().getFirst();

        assertEquals(
                DisclosureStatus.DISCLOSED,
                row.report().disclosureStatus()
        );
        assertEquals("RCE in gateway", row.report().title());
    }

    /**
     * Nulling the title in the response is not enough on its own. If the
     * search still matched undisclosed titles, a caller could recover one a
     * guess at a time by watching which terms return the row.
     */
    @Test
    void searchDoesNotMatchTheTitleOfAnUndisclosedReport() {

        assertEquals(0, count("Stored XSS"));
        assertEquals(0, count("stored"));
        assertEquals(0, count("profile"));

        // The same row is still reachable by everything that is public.
        assertEquals(1, count("sokha"));
    }

    // ------------------------------------------------ the server-side query

    @Test
    void searchMatchesOnTitleAcrossTheWholeFeedNotOneLoadedPage() {

        assertEquals(
                List.of("RCE in gateway"),
                titles(new HacktivityFilter(
                        null, null, null, "rce", null, null
                ))
        );
    }

    @Test
    void searchAlsoMatchesResearcherHandleFullNameAndProgram() {

        assertEquals(1, count("sokha"));
        assertEquals(1, count("Chan Sok"));
        assertEquals(2, count("Acme Web"));
    }

    @Test
    void aWildcardInTheSearchTermIsMatchedLiterally() {

        // Unescaped, "%" would match every row rather than none.
        assertEquals(0, count("%"));
        assertEquals(0, count("_"));
    }

    @Test
    void severityAndEventTypeNarrowTheFeed() {

        assertEquals(
                List.of("RCE in gateway"),
                titles(new HacktivityFilter(
                        null, null, null, null,
                        List.of(Severity.CRITICAL), null
                ))
        );

        // The undisclosed row, so its title is withheld — the filter is what
        // is under test here, not the title.
        assertEquals(
                Collections.singletonList(null),
                titles(new HacktivityFilter(
                        null, null, null, null, null,
                        List.of(HacktivityEventType.RECOGNITION_AWARDED)
                ))
        );

        // Repeatable, so both together are the whole feed back.
        assertEquals(2, hacktivityService.search(
                new HacktivityFilter(
                        null, null, null, null,
                        List.of(Severity.CRITICAL, Severity.MEDIUM), null
                ),
                PageRequest.of(0, 10)
        ).getTotalElements());
    }

    @Test
    void programAndOrganizationScopeTheSameFeed() {

        assertEquals(2, hacktivityService.search(
                new HacktivityFilter(
                        null, acme.getId(), gateway.getId(), null, null, null
                ),
                PageRequest.of(0, 10)
        ).getTotalElements());

        assertEquals(0, hacktivityService.search(
                new HacktivityFilter(
                        null, UUID.randomUUID(), null, null, null, null
                ),
                PageRequest.of(0, 10)
        ).getTotalElements());
    }

    @Test
    void filtersCombineRatherThanReplacingEachOther() {

        assertEquals(0, hacktivityService.search(
                new HacktivityFilter(
                        dara.getId(), null, null, null,
                        List.of(Severity.MEDIUM), null
                ),
                PageRequest.of(0, 10)
        ).getTotalElements());
    }

    // -------------------------------------------------------- paging contract

    @Test
    void theFeedIsNewestFirstByDefault() {

        // By researcher rather than title: the older row is undisclosed, so
        // its title is withheld and cannot carry the ordering.
        assertEquals(
                List.of("dara", "sokha"),
                hacktivityService.search(
                        filter(),
                        HacktivityPaging.resolve(PageRequest.of(0, 10))
                ).getContent().stream()
                        .map(row -> row.user().username())
                        .toList()
        );
    }

    @Test
    void anUnknownSortIsRejectedRatherThanReachingTheQuery() {

        ResponseStatusException rejected = assertThrows(
                ResponseStatusException.class,
                () -> HacktivityPaging.resolve(PageRequest.of(
                        0, 10, Sort.by("dropTable")
                ))
        );

        assertEquals(400, rejected.getStatusCode().value());
        assertTrue(rejected.getReason().contains("createdAt"));
    }

    @Test
    void severityIsSortableAndRunsAgainstTheDatabase() {

        assertEquals(2, hacktivityService.search(
                filter(),
                HacktivityPaging.resolve(PageRequest.of(
                        0, 10, Sort.by("severity")
                ))
        ).getContent().size());
    }

    @Test
    void anOversizedPageIsCappedRatherThanRefused() {

        assertEquals(
                HacktivityPaging.MAX_PAGE_SIZE,
                HacktivityPaging.resolve(PageRequest.of(0, 5000))
                        .getPageSize()
        );
    }

    // ---------------------------------------------------------------- stats

    @Test
    void statsCountTheWholeStreamRatherThanOnePage() {

        HacktivityStatsResponse stats = hacktivityService.getStats();

        assertEquals(2, stats.disclosures());
        assertEquals(2, stats.researchers());
        // Both entries are against the one program.
        assertEquals(1, stats.programsActive());
        assertEquals(
                0,
                new BigDecimal("20500.00").compareTo(stats.totalPaid())
        );
        assertEquals("USD", stats.currency());
    }

    @Test
    void statsOnAnEmptyFeedAreZeroRatherThanNull() {

        hacktivityRepository.deleteAll();

        HacktivityStatsResponse stats = hacktivityService.getStats();

        assertEquals(0, stats.disclosures());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.totalPaid()));
    }

    // ------------------------------------------------------------- fixtures

    private HacktivityFilter filter() {
        return new HacktivityFilter(null, null, null, null, null, null);
    }

    private List<HacktivityResponse> newestFirst() {
        return hacktivityService.search(
                filter(),
                HacktivityPaging.resolve(PageRequest.of(0, 10))
        ).getContent();
    }

    private List<String> titles(HacktivityFilter filter) {
        return hacktivityService.search(filter, PageRequest.of(0, 10))
                .getContent().stream()
                .map(row -> row.report().title())
                .toList();
    }

    private long count(String q) {
        return hacktivityService.search(
                new HacktivityFilter(null, null, null, q, null, null),
                PageRequest.of(0, 10)
        ).getTotalElements();
    }

    private UserProfile profile(
            String username,
            String fullName,
            int reputation
    ) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(username + "@example.test");
        profile.setUsername(username);
        profile.setFullName(fullName);
        profile.setStatus(UserStatus.ACTIVE);
        profile.setReputation(reputation);
        return profile;
    }

    private Organization organization(String name, String slug) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(slug);
        organization.setLogoUrl("https://example.test/logos/" + slug + ".png");
        organization.setOwnerJobTitle("CTO");
        organization.setCompanySize("11-50");
        organization.setCountry("KH");
        organization.setJoiningReason("Running a bounty programme");
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setOwner(dara);
        return organization;
    }

    private Program program(UUID organizationId, String name, String handle) {
        return Program.builder()
                .organizationId(organizationId)
                .name(name)
                .handle(handle)
                .build();
    }

    private Weakness weakness(String cweId, String name) {
        return Weakness.builder()
                .cweId(cweId)
                .name(name)
                .build();
    }

    private Report report(
            Program program,
            UserProfile reporter,
            String title,
            Severity severity,
            DisclosureStatus disclosureStatus,
            Weakness weakness
    ) {
        return Report.builder()
                .program(program)
                .reporter(reporter)
                .title(title)
                .vulnerabilityInformation("Details of " + title)
                .reportedSeverity(severity)
                .severity(severity)
                .weakness(weakness)
                .state(ReportState.RESOLVED)
                .disclosureStatus(disclosureStatus)
                .build();
    }

    private ReportReward reward(Report report, BigDecimal amount, int points) {
        return ReportReward.builder()
                .report(report)
                .amount(amount)
                .points(points)
                .awardedBy(dara)
                .build();
    }

    private Hacktivity entry(
            Report report,
            UserProfile user,
            HacktivityEventType eventType,
            LocalDateTime createdAt
    ) {
        Recognition recognition = new Recognition();
        recognition.setUserId(user.getId());
        recognition.setProgramId(report.getProgram().getId());
        recognition.setReportId(report.getId());
        recognition.setTitle("Recognised: " + report.getTitle());
        recognition.setDescription("Nice find");
        recognition.setAwardedBy(user.getId());
        recognition.setAwardedAt(createdAt);
        recognition.setSeverity(report.getSeverity());

        return Hacktivity.builder()
                .recognition(recognitionRepository.save(recognition))
                .user(user)
                .organization(acme)
                .report(report)
                .program(report.getProgram())
                .eventType(eventType)
                .createdAt(createdAt)
                .build();
    }

    /** A resolution or a disclosure: a feed row with no recognition. */
    private Hacktivity entryWithoutRecognition(
            Report report,
            UserProfile user,
            HacktivityEventType eventType,
            LocalDateTime createdAt
    ) {
        return Hacktivity.builder()
                .recognition(null)
                .user(user)
                .organization(acme)
                .report(report)
                .program(report.getProgram())
                .eventType(eventType)
                .createdAt(createdAt)
                .build();
    }
}
