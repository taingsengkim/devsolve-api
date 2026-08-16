package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.springframework.stereotype.Component;

@Component
public class DisputeMapper {

    public DisputeResponse toResponse(Dispute dispute) {
        Report report = dispute.getReport();
        return new DisputeResponse(
                dispute.getId(),
                report.getId(),
                report.getTitle(),
                report.getProgram().getId(),
                report.getProgram().getName(),
                report.getProgram().getOrganizationId(),
                report.getReporter().getId(),
                report.getReportedSeverity(),
                report.getTriageSeverity(),
                report.getCvssVector(),
                report.getCvssScore(),
                dispute.getStatus(),
                dispute.getRaisedBy() == null
                        ? null
                        : dispute.getRaisedBy().getId(),
                dispute.getReason(),
                dispute.getResolvedSeverity(),
                dispute.getResolvedBy() == null
                        ? null
                        : dispute.getResolvedBy().getId(),
                dispute.getResolutionNotes(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt()
        );
    }
}
