package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;

import java.math.BigDecimal;

/**
 * An organization asking the researcher who found a bug to confirm the fix.
 *
 * <p>Everything here is optional but {@code notes} is what makes a retest
 * answerable: the researcher has to know which build to test and where. It is
 * not enforced, because an organization that has already said all of that in
 * the report thread should not have to repeat itself into a form.
 *
 * @param bountyReward a bonus for the verification work itself, over and above
 *                     whatever the finding was already paid. Committed here and
 *                     paid when the researcher reports back — see
 *                     {@code ReportRetest#bountyReward}.
 */
public record RequestRetestRequest(

        ReportEnvironment environment,

        @Size(
                max = 1000,
                message = "Target endpoint must not exceed 1000 characters"
        )
        String targetEndpoint,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        @DecimalMin(
                value = "0.01",
                message = "A retest bounty must be greater than zero"
        )
        @Digits(
                integer = 8,
                fraction = 2,
                message = "A retest bounty must fit NUMERIC(10,2)"
        )
        BigDecimal bountyReward
) {
}
