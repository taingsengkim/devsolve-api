package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of a report response that are worked out rather than copied.
 *
 * <p>A triage queue reads these before it reads anything else — who filed it,
 * which company it is against, and whether anybody is arguing about it — and
 * each one is derived from somewhere the report itself does not hold.
 */
class ReportMapperEnrichmentTest {

    private final ReportMapper mapper = new ReportMapper();

    @Test
    void theResearcherAndTheirStandingTravelWithTheReport() {
        Report report = report();
        UserProfile reporter = report.getReporter();

        ReportResponse.ResearcherSummary researcher =
                mapper.toResponse(report, organization()).researcher();

        assertEquals(reporter.getId(), researcher.id());
        assertEquals("spiderkim", researcher.username());
        assertEquals("Spider Kim", researcher.fullName());
        assertEquals("spiderkim@example.test", researcher.email());
        assertEquals(1420, researcher.reputation());
        assertEquals(28, researcher.totalReports());
        assertEquals(24, researcher.validReports());
        assertEquals("KH", researcher.country());
    }

    @Test
    void theProgramCarriesTheCompanyBehindIt() {
        ReportResponse.ProgramSummary program =
                mapper.toResponse(report(), organization()).program();

        assertEquals("CyberShield Core Services", program.name());
        assertEquals("cyber-shield-vdp-2026", program.handle());
        assertEquals("CyberShield Inc.", program.organizationName());
        assertEquals("cyber-shield", program.organizationSlug());
        assertEquals("https://files.test/logo.jpg", program.organizationLogoUrl());
    }

    /**
     * A report outlives the company that received it. Blanking the row would
     * hide exactly the findings nobody is left to answer for.
     */
    @Test
    void aReportWhoseCompanyIsGoneStillRenders() {
        ReportResponse response = mapper.toResponse(report(), null);

        assertEquals(
                "CyberShield Core Services",
                response.program().name()
        );
        assertNull(response.program().organizationName());
        assertNull(response.program().organizationSlug());
        assertNull(response.program().organizationLogoUrl());
        // The id is on the program itself, so it survives either way.
        assertEquals(
                report().getProgram().getOrganizationId().getClass(),
                response.program().organizationId().getClass()
        );
    }

    @Test
    void engagementTypeIsReadFromTheProgram() {
        assertEquals(
                EngagementType.BOUNTY,
                mapper.toResponse(report(), organization()).type()
        );
    }

    @Test
    void theShortReferenceIsDerivedFromTheId() {
        Report report = report();

        assertEquals(
                "RPT-" + report.getId()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase(),
                mapper.toResponse(report, organization()).reportId()
        );
    }

    /**
     * The source is markdown. A summary that opens on a blank line or a fenced
     * block renders as an empty row in the queue.
     */
    @Test
    void theSummaryIsOneLineAndCutOnAWord() {
        Report report = report();
        report.setVulnerabilityInformation(
                "  The profile endpoint\n\n   leaks other users' records "
                        + "when the id is changed.  "
        );

        assertEquals(
                "The profile endpoint leaks other users' records when the id "
                        + "is changed.",
                mapper.toResponse(report, organization()).summary()
        );
    }

    @Test
    void aLongSummaryIsTruncatedWithAnEllipsis() {
        Report report = report();
        report.setVulnerabilityInformation("word ".repeat(200));

        String summary = mapper.toResponse(report, organization()).summary();

        assertTrue(summary.endsWith("…"));
        assertTrue(summary.length() <= 221);
    }

    @Test
    void anEmptyBodyProducesNoSummaryRatherThanAnEmptyString() {
        Report report = report();
        report.setVulnerabilityInformation("   \n  ");

        assertNull(mapper.toResponse(report, organization()).summary());
    }

    /**
     * Open right now, not ever raised. A queue that coloured settled rows keeps
     * flagging findings both sides stopped arguing about weeks ago.
     */
    @Test
    void onlyAnOpenDisputeMarksTheReportDisputed() {
        assertFalse(disputedWith(DisputeStatus.RESOLVED));
        assertFalse(disputedWith(DisputeStatus.DISMISSED));

        assertTrue(disputedWith(DisputeStatus.OPEN));
        assertTrue(disputedWith(DisputeStatus.UNDER_REVIEW));
        // Triage rated it differently and the reporter has not answered — the
        // disagreement is live even though the dispute proper is not open yet.
        assertTrue(disputedWith(DisputeStatus.AWAITING_REPORTER));
    }

    @Test
    void aReportNobodyHasArguedAboutIsNotDisputed() {
        assertFalse(mapper.toResponse(report(), organization()).isDisputed());
    }

    /**
     * Both ratings, always, and the settled one left null while it is still
     * being argued about.
     *
     * <p>A queue that showed one number could only show the wrong one: a
     * researcher who claimed HIGH and a company that triaged it LOW are both
     * looking at the same row, and neither rating is the answer until they
     * agree. Blanking the response down to {@code severity} is what makes a
     * triaged-down finding read as unrated.
     */
    @Test
    void bothRatingsAreCarriedWhileTheSettledOneIsStillOpen() {
        Report report = report();
        report.setReportedSeverity(Severity.MEDIUM);
        report.setTriageSeverity(Severity.LOW);
        report.setSeverity(null);

        ReportResponse response = mapper.toResponse(report, organization());

        assertEquals(Severity.MEDIUM, response.reportedSeverity());
        assertEquals(Severity.LOW, response.triageSeverity());
        assertNull(response.severity());
    }

    @Test
    void aSettledRatingLeavesBothClaimsInPlace() {
        Report report = report();
        report.setReportedSeverity(Severity.MEDIUM);
        report.setTriageSeverity(Severity.LOW);
        report.setSeverity(Severity.LOW);

        ReportResponse response = mapper.toResponse(report, organization());

        assertEquals(Severity.MEDIUM, response.reportedSeverity());
        assertEquals(Severity.LOW, response.triageSeverity());
        assertEquals(Severity.LOW, response.severity());
    }

    /**
     * The disagreement itself, not just the flag. {@code isDisputed} says a row
     * needs colouring; this is what tells the reporter it is their turn.
     */
    @Test
    void theOpenDisputeTravelsWithTheReport() {
        Report report = report();
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setStatus(DisputeStatus.AWAITING_REPORTER);
        dispute.setReason("Triage rated the finding below the reported severity");
        dispute.setRespondBy(LocalDateTime.of(2026, 9, 14, 12, 0));
        report.getDisputes().add(dispute);

        ReportResponse.DisputeSummary summary =
                mapper.toResponse(report, organization()).dispute();

        assertEquals(dispute.getId(), summary.id());
        assertEquals(DisputeStatus.AWAITING_REPORTER, summary.status());
        assertEquals(
                "Triage rated the finding below the reported severity",
                summary.reason()
        );
        assertEquals(dispute.getRespondBy(), summary.respondBy());
    }

    /**
     * The newest one. A report can carry a settled disagreement and a live one,
     * and the live one is what either side is waiting on.
     */
    @Test
    void theNewestDisputeIsTheOneCarried() {
        Report report = report();
        report.getDisputes().add(dispute(
                DisputeStatus.RESOLVED,
                LocalDateTime.of(2026, 8, 1, 9, 0)
        ));
        Dispute latest = dispute(
                DisputeStatus.AWAITING_REPORTER,
                LocalDateTime.of(2026, 9, 1, 9, 0)
        );
        report.getDisputes().add(latest);

        assertEquals(
                latest.getId(),
                mapper.toResponse(report, organization()).dispute().id()
        );
    }

    /**
     * The reporter's own wording for a class the catalog does not have. Held on
     * the report rather than in the catalog, so it has nowhere else to be read
     * from — dropping it from the response loses it entirely.
     */
    @Test
    void aWeaknessTheCatalogDoesNotHaveIsStillNamed() {
        Report report = report();
        report.setSuggestedWeakness("Prompt injection via tool output");

        ReportResponse response = mapper.toResponse(report, organization());

        assertEquals(
                "Prompt injection via tool output",
                response.suggestedWeakness()
        );
        // Mutually exclusive with the catalog entry, and neither is set here.
        assertNull(response.weakness());
    }

    private Dispute dispute(DisputeStatus status, LocalDateTime createdAt) {
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setStatus(status);
        dispute.setCreatedAt(createdAt);
        return dispute;
    }

    private boolean disputedWith(DisputeStatus status) {
        Report report = report();
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setStatus(status);
        report.getDisputes().add(dispute);
        return mapper.toResponse(report, organization()).isDisputed();
    }

    private Organization organization() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setName("CyberShield Inc.");
        organization.setSlug("cyber-shield");
        organization.setLogoUrl("https://files.test/logo.jpg");
        return organization;
    }

    private Report report() {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setName("CyberShield Core Services");
        program.setHandle("cyber-shield-vdp-2026");
        program.setEngagementType(EngagementType.BOUNTY);

        UserProfile reporter = new UserProfile();
        reporter.setId(UUID.randomUUID());
        reporter.setUsername("spiderkim");
        reporter.setFullName("Spider Kim");
        reporter.setEmail("spiderkim@example.test");
        reporter.setAvatarUrl("https://files.test/avatar.jpg");
        reporter.setReputation(1420);
        reporter.setTotalReports(28);
        reporter.setValidReports(24);
        reporter.setCountry("KH");

        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setProgram(program);
        report.setReporter(reporter);
        report.setTitle("IDOR in User Profile Endpoint");
        report.setVulnerabilityInformation("A short description.");
        return report;
    }
}
