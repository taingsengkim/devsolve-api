package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds report responses with the company behind each one attached.
 *
 * <p>A {@link kh.edu.istad.ite.devsoleapi.feature.program.Program} holds its
 * organization as a bare UUID with no mapped association, so the mapper cannot
 * reach it and the company's name and logo have to be looked up. Doing that per
 * row is a query per report — the N+1 this enrichment exists to avoid — so a
 * page resolves every distinct organization it needs in one go.
 *
 * <p>Every path that returns a report goes through here, single and paged
 * alike. A second way to build the response is how a queue ends up showing the
 * company on a listing and null on the detail page behind it.
 */
@Component
@RequiredArgsConstructor
public class ReportResponseAssembler {

    private final OrganizationRepository organizationRepository;
    private final ReportMapper reportMapper;

    public ReportResponse one(Report report) {
        return reportMapper.toResponse(
                report,
                organizationOf(report)
        );
    }

    /**
     * One page, and one organization lookup however many rows it holds.
     *
     * <p>Twenty reports against three programs of the same company cost one
     * query here and twenty in the obvious version.
     */
    public Page<ReportResponse> page(Page<Report> reports) {
        Map<UUID, Organization> organizations =
                organizationsFor(reports.getContent());
        return reports.map(report -> reportMapper.toResponse(
                report,
                organizations.get(report.getProgram().getOrganizationId())
        ));
    }

    public List<ReportResponse> list(List<Report> reports) {
        Map<UUID, Organization> organizations = organizationsFor(reports);
        return reports.stream()
                .map(report -> reportMapper.toResponse(
                        report,
                        organizations.get(
                                report.getProgram().getOrganizationId()
                        )
                ))
                .toList();
    }

    private Organization organizationOf(Report report) {
        UUID organizationId = report.getProgram().getOrganizationId();
        if (organizationId == null) {
            return null;
        }
        return organizationRepository.findById(organizationId).orElse(null);
    }

    /**
     * Deleted organizations are included on purpose — {@code findAllById} finds
     * them, since the row is only soft-deleted. A report outlives the company
     * that received it, and a triage queue that blanked those rows would hide
     * exactly the findings nobody is left to answer for.
     */
    private Map<UUID, Organization> organizationsFor(List<Report> reports) {
        Set<UUID> ids = reports.stream()
                .map(report -> report.getProgram().getOrganizationId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return organizationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        Organization::getId,
                        Function.identity()
                ));
    }
}
