package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why the reporter will not accept the severity triage gave their finding.
 *
 * <p>Required, and the only thing this endpoint takes. An administrator is
 * about to be asked to rule between two ratings, and "I disagree" gives them
 * nothing to rule on — the reason is the case, so refusing without one would
 * just move the argument rather than start it.
 */
public record RejectTriageSeverityRequest(

        @NotBlank(message = "A reason is required")
        @Size(
                max = 5000,
                message = "Reason must not exceed 5000 characters"
        )
        String reason
) {
}
