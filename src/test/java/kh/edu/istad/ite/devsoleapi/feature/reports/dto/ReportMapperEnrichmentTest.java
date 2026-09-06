package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.Test;

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
