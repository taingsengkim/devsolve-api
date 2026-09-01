package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Writes the feed entries that are not recognitions.
 *
 * <p>Lives here rather than in the report service so the feed's own package
 * owns what reaches the feed, and so the report service takes one dependency
 * instead of three.
 *
 * <p>Nothing here may fail the work that triggered it. A report is resolved
 * because an organization fixed a vulnerability; losing that to a feed write
 * would be absurd, so every failure is logged and swallowed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HacktivityRecorder {

    private final HacktivityRepository hacktivityRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * A report reached RESOLVED: the organization fixed the vulnerability.
     *
     * <p>Safe to publish precisely because it is fixed. The title still only
     * appears once the report is disclosed — see {@code HacktivityMapper} —
     * so this says a finding of that severity was resolved, not what it was.
     */
    public void recordResolved(Report report) {
        record(report, HacktivityEventType.REPORT_RESOLVED);
    }

    /** A report became publicly disclosed. */
    public void recordDisclosed(Report report) {
        record(report, HacktivityEventType.REPORT_DISCLOSED);
    }

    private void record(Report report, HacktivityEventType eventType) {
        try {
            write(report, eventType);
        } catch (RuntimeException exception) {
            log.error(
                    "Could not record a {} hacktivity entry for report {}",
                    eventType,
                    report.getId(),
                    exception
            );
        }
    }

    private void write(Report report, HacktivityEventType eventType) {
        Program program = report.getProgram();

        // A failed retest reopens a resolved report, so a report can reach
        // RESOLVED more than once. The feed should say it was resolved, not
        // count how many attempts it took to make that stick.
        if (hacktivityRepository.existsByReportIdAndEventType(
                report.getId(),
                eventType
        )) {
            return;
        }

        Optional<Organization> organization = organizationRepository
                .findById(program.getOrganizationId());

        if (organization.isEmpty()) {
            // Every other column on the row is NOT NULL, so there is no
            // half-row worth writing. A program whose organization has gone
            // is already broken in ways this is not the place to fix.
            log.warn(
                    "Skipped a {} hacktivity entry for report {}: program {}"
                            + " has no organization {}",
                    eventType,
                    report.getId(),
                    program.getId(),
                    program.getOrganizationId()
            );
            return;
        }

        hacktivityRepository.save(
                Hacktivity.builder()
                        // No recognition: the report was fixed or published
                        // whether or not anybody was credited for it.
                        .recognition(null)
                        .user(report.getReporter())
                        .organization(organization.get())
                        .report(report)
                        .program(program)
                        .eventType(eventType)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }
}
