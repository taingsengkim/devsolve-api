package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;

import java.util.UUID;

/**
 * {@code weaknessId} is where a report actually gets classified. The catalog
 * is a closed vocabulary a reporter picks from and may leave unset, so the
 * triager — who knows the taxonomy and has read the finding — is the one who
 * settles which class it belongs to, or corrects a wrong pick.
 *
 * <p>Left null it keeps whatever the report already has, the way the other
 * optional fields on this patch do; there is no way to clear a classification
 * back to none, only to replace it with a better one.
 */
public record TriageReportRequest(
        @NotNull(message = "Triage severity is required")
        Severity triageSeverity,

        ReportState state,

        UUID duplicateOfId,

        UUID weaknessId
) {
}
