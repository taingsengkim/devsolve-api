package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;

import java.util.UUID;

/**
 * @param status null when the researcher has never approached this company,
 *               which reads differently from having been turned down.
 * @param reason plain-language explanation, safe to show as-is.
 */
public record ReportingEligibilityResponse(
        UUID programId,
        UUID organizationId,
        String organizationName,
        boolean canSubmitReports,
        ResearcherAccessStatus status,
        String reason
) {
}
