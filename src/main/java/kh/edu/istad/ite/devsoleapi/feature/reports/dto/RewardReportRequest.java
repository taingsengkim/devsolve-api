package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A payout an organization records against a report.
 *
 * <p>Money only, deliberately. A reward used to be able to carry reputation
 * points as well, which let one organization decide where a researcher sat on
 * a leaderboard that spans every organization — priced at 100 points for a
 * critical finding, a single mistyped reward was worth ten thousand of them,
 * and nothing in the platform ever subtracts reputation again.
 *
 * <p>A bounty is the organization's to set: it comes out of their budget and
 * concerns only them and the researcher. Reputation is a shared ranking, so it
 * is priced in one place, by severity, and paid automatically when the report
 * is resolved — see {@code ReputationPolicy}. A researcher on a paying program
 * earns both; this is only the half the organization decides.
 */
public record RewardReportRequest(

        @NotNull(message = "A reward amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than zero"
        )
        @Digits(
                integer = 8,
                fraction = 2,
                message = "Amount must fit NUMERIC(10,2)"
        )
        BigDecimal amount,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
