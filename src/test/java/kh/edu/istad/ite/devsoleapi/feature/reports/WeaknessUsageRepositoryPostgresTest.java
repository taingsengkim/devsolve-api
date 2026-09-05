package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aggregates behind "which weakness do we actually receive", against a real
 * PostgreSQL.
 *
 * <p>Nothing here can be checked in a mocked test. The catalog usage query is a
 * left join to an entity with no mapped association back, a {@code having} over
 * a count, and a {@code sum(case ...)} against {@code report_state_enum} — a
 * named enum type Hibernate binds as an untyped parameter. The suggestion query
 * groups on an expression. Every one of those either works in the database or
 * does not, and a mock would agree with whatever it was told.
 *
 * <p>Assertions are on the rows this test creates rather than on totals: the
 * production schema.sql seeds a starting catalog, and a test that assumed an
 * empty one would break the day somebody adds a CWE to that seed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class WeaknessUsageRepositoryPostgresTest {

    private static final Set<ReportState> VALID_STATES = Set.of(
            ReportState.VALID_CONFIRMED,
            ReportState.RETESTING,
            ReportState.RESOLVED
    );

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private WeaknessRepository weaknessRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    @Transactional
    void popularCountsEveryReportAndAgreedOnesSeparately() {
        Weakness busy = persistedWeakness("Session fixation " + suffix());
        Weakness quiet = persistedWeakness("Open redirect " + suffix());
        Program program = persistedProgram();
        UserProfile reporter = persistedProfile();

        persistedReport(program, reporter, busy, ReportState.RESOLVED);
        persistedReport(program, reporter, busy, ReportState.VALID_CONFIRMED);
        persistedReport(program, reporter, busy, ReportState.DUPLICATE);
        persistedReport(program, reporter, quiet, ReportState.NEW);

        Map<UUID, WeaknessUsageProjection> byId = usage();

        WeaknessUsageProjection busyRow = byId.get(busy.getId());
        assertNotNull(busyRow);
        assertEquals(3L, busyRow.getReportCount());
        // The duplicate is still something the platform received, so it counts
        // once and is left out of the agreed figure.
        assertEquals(2L, busyRow.getValidCount());
        assertNotNull(busyRow.getLastReportedAt());

        WeaknessUsageProjection quietRow = byId.get(quiet.getId());
        assertNotNull(quietRow);
        assertEquals(1L, quietRow.getReportCount());
        // NEW is nobody's verdict yet.
        assertEquals(0L, quietRow.getValidCount());
    }

    /**
     * The busiest class comes first, and an entry nothing has been filed under
     * is not in the answer at all.
     */
    @Test
    @Transactional
    void popularOrdersByVolumeAndDropsWhatNobodyReports() {
        Weakness reported = persistedWeakness("Mass assignment " + suffix());
        Weakness never = persistedWeakness("Unused class " + suffix());
        Program program = persistedProgram();
        UserProfile reporter = persistedProfile();
        persistedReport(program, reporter, reported, ReportState.RESOLVED);

        List<WeaknessUsageProjection> top = weaknessRepository.findActiveUsage(
                VALID_STATES,
                1L,
                PageRequest.of(0, 50)
        ).getContent();

        Set<UUID> ids = top.stream()
                .map(WeaknessUsageProjection::getId)
                .collect(Collectors.toSet());
        assertTrue(ids.contains(reported.getId()));
        assertFalse(ids.contains(never.getId()));

        // Descending by count, which is the whole ordering contract: the caller
        // cannot re-sort this listing, so the query owes it.
        long previous = Long.MAX_VALUE;
        for (WeaknessUsageProjection row : top) {
            assertTrue(row.getReportCount() <= previous);
            previous = row.getReportCount();
        }
    }

    /**
     * The administrator's view of the same figures: unused entries included, and
     * retired ones too — the history under a retired class is exactly what says
     * whether retiring it was right.
     */
    @Test
    @Transactional
    void adminUsageSeesUnusedAndRetiredEntries() {
        Weakness never = persistedWeakness("Never filed " + suffix());
        Weakness retired = persistedWeakness("Retired class " + suffix());
        retired.setIsActive(false);
        weaknessRepository.saveAndFlush(retired);
        persistedReport(
                persistedProgram(),
                persistedProfile(),
                retired,
                ReportState.RESOLVED
        );

        Map<UUID, WeaknessUsageProjection> all = weaknessRepository
                .findAllUsage(VALID_STATES, 0L, PageRequest.of(0, 100))
                .getContent()
                .stream()
                .collect(Collectors.toMap(
                        WeaknessUsageProjection::getId,
                        Function.identity()
                ));

        WeaknessUsageProjection neverRow = all.get(never.getId());
        assertNotNull(neverRow);
        assertEquals(0L, neverRow.getReportCount());
        assertEquals(0L, neverRow.getValidCount());
        assertNull(neverRow.getLastReportedAt());

        WeaknessUsageProjection retiredRow = all.get(retired.getId());
        assertNotNull(retiredRow);
        assertEquals(1L, retiredRow.getReportCount());
        assertFalse(retiredRow.getIsActive());

        // The reporter-facing list is the same query minus both of those.
        Set<UUID> active = weaknessRepository
                .findActiveUsage(VALID_STATES, 1L, PageRequest.of(0, 100))
                .getContent()
                .stream()
                .map(WeaknessUsageProjection::getId)
                .collect(Collectors.toSet());
        assertFalse(active.contains(never.getId()));
        assertFalse(active.contains(retired.getId()));
    }

    /**
     * Three spellings of one missing class are one gap, not three. This is the
     * whole reason the suggestions are grouped rather than listed.
     */
    @Test
    @Transactional
    void reporterSuggestionsGroupCaseInsensitivelyAndTrimmed() {
        Program program = persistedProgram();
        UserProfile reporter = persistedProfile();
        String gap = "Prototype pollution " + suffix();

        persistedSuggestion(program, reporter, gap.toUpperCase());
        persistedSuggestion(program, reporter, "  " + gap.toLowerCase() + " ");
        persistedSuggestion(program, reporter, gap);

        SuggestedWeaknessProjection row = reportRepository
                .findSuggestedWeaknesses(PageRequest.of(0, 100))
                .getContent()
                .stream()
                .filter(candidate -> gap.toLowerCase()
                        .equals(candidate.getNormalized()))
                .findFirst()
                .orElseThrow();

        assertEquals(3L, row.getReportCount());
        assertNotNull(row.getName());
        assertNotNull(row.getFirstSuggestedAt());
        assertNotNull(row.getLastSuggestedAt());
    }

    /**
     * A suggestion whose name the catalog already carries means the picker
     * failed, not the vocabulary — so the read side has to be able to tell.
     */
    @Test
    @Transactional
    void catalogNamesAreFoundCaseInsensitively() {
        Weakness existing = persistedWeakness("Race Condition " + suffix());
        String name = existing.getName();

        List<String> found = weaknessRepository.findNamesInLowerCase(List.of(
                name.toLowerCase(),
                "nothing by this name at all"
        ));

        assertEquals(List.of(name.toLowerCase()), found);
    }

    /**
     * The denominator behind the share figure counts classified reports only.
     */
    @Test
    @Transactional
    void unclassifiedReportsAreNotCountedAsAClass() {
        Program program = persistedProgram();
        UserProfile reporter = persistedProfile();
        Weakness weakness = persistedWeakness("Counted " + suffix());
        long before = reportRepository.countByWeaknessIsNotNull();

        persistedReport(program, reporter, weakness, ReportState.NEW);
        persistedReport(program, reporter, null, ReportState.NEW);

        assertEquals(before + 1, reportRepository.countByWeaknessIsNotNull());
    }

    private Map<UUID, WeaknessUsageProjection> usage() {
        return weaknessRepository
                .findActiveUsage(VALID_STATES, 1L, PageRequest.of(0, 100))
                .getContent()
                .stream()
                .collect(Collectors.toMap(
                        WeaknessUsageProjection::getId,
                        Function.identity()
                ));
    }

    private Weakness persistedWeakness(String name) {
        return weaknessRepository.saveAndFlush(
                Weakness.builder()
                        .name(name)
                        .isActive(true)
                        .build()
        );
    }

    private Report persistedReport(
            Program program,
            UserProfile reporter,
            Weakness weakness,
            ReportState state
    ) {
        return reportRepository.saveAndFlush(
                Report.builder()
                        .program(program)
                        .reporter(reporter)
                        .title("Broken access control")
                        .vulnerabilityInformation("A user reads another.")
                        .reportedSeverity(Severity.HIGH)
                        .weakness(weakness)
                        .state(state)
                        .build()
        );
    }

    private Report persistedSuggestion(
            Program program,
            UserProfile reporter,
            String suggested
    ) {
        return reportRepository.saveAndFlush(
                Report.builder()
                        .program(program)
                        .reporter(reporter)
                        .title("Something the catalog does not cover")
                        .vulnerabilityInformation("Details.")
                        .reportedSeverity(Severity.MEDIUM)
                        .suggestedWeakness(suggested)
                        .state(ReportState.NEW)
                        .build()
        );
    }

    private Program persistedProgram() {
        return programRepository.saveAndFlush(
                Program.builder()
                        .organizationId(UUID.randomUUID())
                        .name("Gateway")
                        .handle("gateway-" + UUID.randomUUID())
                        .build()
        );
    }

    private UserProfile persistedProfile() {
        String handle = "user" + suffix();
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(handle + "@example.test");
        profile.setUsername(handle);
        profile.setFullName("Test Person");
        profile.setStatus(UserStatus.ACTIVE);
        return userProfileRepository.saveAndFlush(profile);
    }

    /** Keeps every name unique against a catalog schema.sql has already seeded. */
    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
