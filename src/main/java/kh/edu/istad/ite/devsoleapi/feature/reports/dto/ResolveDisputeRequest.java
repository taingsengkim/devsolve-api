package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;

/**
 * An administrator's decision on a severity dispute.
 *
 * @param status         {@code UNDER_REVIEW} to claim the dispute without
 *                       deciding it yet, {@code RESOLVED} to set the final
 *                       severity, or {@code DISMISSED} to let the triage
 *                       assessment stand.
 * @param finalSeverity  required for {@code RESOLVED}, rejected otherwise —
 *                       a dismissal keeps the severity triage already chose.
 * @param resolutionNotes required for a decision. The ruling overrides both
 *                        the reporter and the company, and neither can see why
 *                        unless it is written down.
 */
public record ResolveDisputeRequest(
        @NotNull(message = "Dispute status is required")
        DisputeStatus status,

        Severity finalSeverity,

        @Size(
                max = 2000,
                message = "Resolution notes must not exceed 2000 characters"
        )
        String resolutionNotes
) {
}
