package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The filters behind {@code GET /reports/mine}, against a real PostgreSQL.
 *
 * <p>Neither of the two interesting ones can be checked against a mock. The
 * severity filter is a chain of comparisons against {@code severity_enum}, a
 * named enum type Hibernate binds as an untyped parameter — it works in the
 * database or it does not. The reference filter turns a {@code RPT-} handle
 * back into a range over {@code uuid}, and whether that range holds depends on
 * how Postgres orders the type, not on how Java does.
 *
 * <p>Every query here is scoped to a reporter this test created, so the rows
 * production {@code schema.sql} seeds cannot drift the assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class ReportSpecificationPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    /**
     * A settled rating is the one the filter answers to, whatever the two sides
     * claimed on the way there.
     */
    @Test
    @Transactional
    void aSettledReportFiltersOnTheAgreedRating() {
        UserProfile reporter = persistedProfile();
        Report settled = persistedReport(reporter, report -> {
            report.setReportedSeverity(Severity.CRITICAL);
            report.setTriageSeverity(Severity.HIGH);
            report.setSeverity(Severity.LOW);
        });

        assertEquals(List.of(settled.getId()), idsFor(reporter, Severity.LOW));
        assertEquals(List.of(), idsFor(reporter, Severity.CRITICAL));
        assertEquals(List.of(), idsFor(reporter, Severity.HIGH));
    }

    /**
     * The case the filter exists for. Triage rated it down, the reporter has
     * not answered, and the settled column is still null — filtering on that
     * column alone would hide the report from every severity.
     */
    @Test
    @Transactional
    void aReportBeingArguedOverFiltersOnWhatTriageRated() {
        UserProfile reporter = persistedProfile();
        Report disputed = persistedReport(reporter, report -> {
            report.setReportedSeverity(Severity.MEDIUM);
            report.setTriageSeverity(Severity.LOW);
            report.setSeverity(null);
        });

        assertEquals(List.of(disputed.getId()), idsFor(reporter, Severity.LOW));
        assertEquals(List.of(), idsFor(reporter, Severity.MEDIUM));
    }

    /** Nobody has looked at it yet, so the claim is all there is. */
    @Test
    @Transactional
    void anUntriagedReportFiltersOnTheReportersClaim() {
        UserProfile reporter = persistedProfile();
        Report fresh = persistedReport(reporter, report -> {
            report.setReportedSeverity(Severity.HIGH);
            report.setTriageSeverity(null);
            report.setSeverity(null);
        });

        assertEquals(List.of(fresh.getId()), idsFor(reporter, Severity.HIGH));
        assertEquals(List.of(), idsFor(reporter, Severity.LOW));
    }

    @Test
    @Transactional
    void stateNarrowsToOneStepOfTheWorkflow() {
        UserProfile reporter = persistedProfile();
        Report triaging = persistedReport(
                reporter,
                report -> report.setState(ReportState.TRIAGING)
        );
        persistedReport(
                reporter,
                report -> report.setState(ReportState.RESOLVED)
        );

        assertEquals(
                List.of(triaging.getId()),
                ids(mine(reporter).and(
                        ReportSpecification.withState(ReportState.TRIAGING)
                ))
        );
    }

    @Test
    @Transactional
    void searchMatchesTheTitleCaseInsensitively() {
        UserProfile reporter = persistedProfile();
        Report idor = persistedReport(
                reporter,
                report -> report.setTitle("IDOR in the profile endpoint")
        );
        persistedReport(
                reporter,
                report -> report.setTitle("Stored XSS in comments")
        );

        assertEquals(List.of(idor.getId()), idsMatching(reporter, "idor"));
        assertEquals(List.of(idor.getId()), idsMatching(reporter, "  IDOR "));
    }

    @Test
    @Transactional
    void searchReachesTheProgramTheReportWasFiledAgainst() {
        UserProfile reporter = persistedProfile();
        String handle = "acme-" + suffix();
        Report against = persistedReport(
                reporter,
                persistedProgram("ACME Bug Bounty", handle),
                report -> report.setTitle("Nothing in this title matches")
        );
        persistedReport(reporter, report -> { });

        assertEquals(List.of(against.getId()), idsMatching(reporter, "acme bug"));
        assertEquals(List.of(against.getId()), idsMatching(reporter, handle));
    }

    /**
     * The handle the response shows and a researcher pastes back. Derived from
     * the id, so it is matched by un-deriving it rather than by a LIKE.
     */
    @Test
    @Transactional
    void searchMatchesTheShortReferenceTheResponseShows() {
        UserProfile reporter = persistedProfile();
        Report report = persistedReport(reporter, unchanged -> { });
        persistedReport(reporter, unchanged -> { });

        String reference = "RPT-" + report.getId()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);

        assertEquals(List.of(report.getId()), idsMatching(reporter, reference));
        // The bare eight characters work too — people drop the prefix.
        assertEquals(
                List.of(report.getId()),
                idsMatching(reporter, report.getId().toString().substring(0, 8))
        );
    }

    /**
     * An underscore is a single-character wildcard to LIKE. Unescaped, a search
     * for a literal one quietly matches everything the same length.
     */
    @Test
    @Transactional
    void wildcardsInTheSearchTermAreLiteral() {
        UserProfile reporter = persistedProfile();
        Report literal = persistedReport(
                reporter,
                report -> report.setTitle("Leak in get_user")
        );
        persistedReport(
                reporter,
                report -> report.setTitle("Leak in getxuser")
        );

        assertEquals(List.of(literal.getId()), idsMatching(reporter, "get_user"));
    }

    /** Clearing the filter box is not a filter. */
    @Test
    @Transactional
    void blankSearchAndAbsentSeverityFilterNothing() {
        UserProfile reporter = persistedProfile();
        persistedReport(reporter, unchanged -> { });
        persistedReport(reporter, unchanged -> { });

        assertEquals(2, ids(mine(reporter)
                .and(ReportSpecification.matching("   "))
                .and(ReportSpecification.matching(null))
                .and(ReportSpecification.withEffectiveSeverity(null))
                .and(ReportSpecification.withState(null))
        ).size());
    }

    /**
     * The filters are meant to compose, and a report has to satisfy all of them
     * rather than any.
     */
    @Test
    @Transactional
    void theFiltersNarrowTogether() {
        UserProfile reporter = persistedProfile();
        Report wanted = persistedReport(reporter, report -> {
            report.setTitle("SSRF in the webhook fetcher");
            report.setState(ReportState.TRIAGING);
            report.setReportedSeverity(Severity.HIGH);
        });
        persistedReport(reporter, report -> {
            report.setTitle("SSRF in the avatar fetcher");
            report.setState(ReportState.RESOLVED);
            report.setReportedSeverity(Severity.HIGH);
        });
        persistedReport(reporter, report -> {
            report.setTitle("SSRF in the webhook fetcher");
            report.setState(ReportState.TRIAGING);
            report.setReportedSeverity(Severity.LOW);
        });

        List<UUID> found = ids(mine(reporter)
                .and(ReportSpecification.withState(ReportState.TRIAGING))
                .and(ReportSpecification.withEffectiveSeverity(Severity.HIGH))
                .and(ReportSpecification.matching("webhook"))
        );

        assertEquals(List.of(wanted.getId()), found);
    }

    /** One reporter's filters never reach another's reports. */
    @Test
    @Transactional
    void theScopeStillHoldsUnderASearch() {
        UserProfile mine = persistedProfile();
        UserProfile someoneElse = persistedProfile();
        persistedReport(
                someoneElse,
                report -> report.setTitle("Shared title")
        );
        Report ours = persistedReport(
                mine,
                report -> report.setTitle("Shared title")
        );

        assertEquals(List.of(ours.getId()), idsMatching(mine, "shared title"));
    }

    private List<UUID> idsFor(UserProfile reporter, Severity severity) {
        return ids(mine(reporter).and(
                ReportSpecification.withEffectiveSeverity(severity)
        ));
    }

    private List<UUID> idsMatching(UserProfile reporter, String search) {
        return ids(mine(reporter).and(ReportSpecification.matching(search)));
    }

    private List<UUID> ids(Specification<Report> specification) {
        return reportRepository
                .findAll(specification, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(Report::getId)
                .toList();
    }

    private Specification<Report> mine(UserProfile reporter) {
        return ReportSpecification.submittedBy(reporter.getId());
    }

    private Report persistedReport(
            UserProfile reporter,
            Consumer<Report> customise
    ) {
        return persistedReport(
                reporter,
                persistedProgram("Gateway", "gateway-" + suffix()),
                customise
        );
    }

    private Report persistedReport(
            UserProfile reporter,
            Program program,
            Consumer<Report> customise
    ) {
        Report report = Report.builder()
                .program(program)
                .reporter(reporter)
                .title("Broken access control")
                .vulnerabilityInformation("A user reads another.")
                .reportedSeverity(Severity.MEDIUM)
                .state(ReportState.NEW)
                .build();
        customise.accept(report);
        return reportRepository.saveAndFlush(report);
    }

    private Program persistedProgram(String name, String handle) {
        return programRepository.saveAndFlush(
                Program.builder()
                        .organizationId(UUID.randomUUID())
                        .name(name)
                        .handle(handle)
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

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
