package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;

import java.util.UUID;

public record TriageReportRequest(
        @NotNull(message = "Triage severity is required")
        Severity triageSeverity,

        ReportState state,

        UUID duplicateOfId
) {
}
