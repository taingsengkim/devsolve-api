package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A report can now reach RESOLVED more than once: a failed retest reopens it,
 * and the organization fixes and resolves it again. The public feed should say
 * the finding was resolved, not count how many attempts it took to make that
 * stick — so the entry is written once per report per milestone.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class HacktivityRecorderIdempotencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private HacktivityRecorder hacktivityRecorder;
    @Autowired
    private HacktivityRepository hacktivityRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    @Transactional
    void resolvingTheSameReportTwiceWritesOneFeedEntry() {
        Report report = persistedReport();

        hacktivityRecorder.recordResolved(report);
        hacktivityRecorder.recordResolved(report);

        assertEquals(
                1,
                hacktivityRepository.findAll().stream()
                        .filter(entry -> entry.getReport().getId()
                                .equals(report.getId()))
                        .filter(entry -> entry.getEventType()
                                == HacktivityEventType.REPORT_RESOLVED)
                        .count()
        );
    }

    /** A resolution and a disclosure are different milestones on one report. */
    @Test
    @Transactional
    void aDisclosureIsStillItsOwnEntryOnAResolvedReport() {
        Report report = persistedReport();

        hacktivityRecorder.recordResolved(report);
        hacktivityRecorder.recordDisclosed(report);

        assertEquals(
                2,
                hacktivityRepository.findAll().stream()
                        .filter(entry -> entry.getReport().getId()
                                .equals(report.getId()))
                        .count()
        );
    }

    private Report persistedReport() {
        UserProfile reporter = persistedProfile();
        Organization organization = new Organization();
        organization.setName("Acme");
        organization.setSlug("acme-" + UUID.randomUUID());
        organization.setLogoUrl("https://example.test/logo.png");
        organization.setOwnerJobTitle("CTO");
        organization.setCompanySize("11-50");
        organization.setCountry("KH");
        organization.setJoiningReason("Running a bounty programme");
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setOwner(reporter);
        organizationRepository.saveAndFlush(organization);

        Program program = programRepository.saveAndFlush(
                Program.builder()
                        .organizationId(organization.getId())
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
                        .state(ReportState.RESOLVED)
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
