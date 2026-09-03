package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A draft read back. Returns the identifiers it was saved with rather than
 * resolved summaries the way {@link ReportResponse} does for its weakness and
 * asset: a draft holds whatever the form held, including a reference that has
 * since been retired, and expanding it here would either hide that or fail the
 * read. The client already has the catalog it picked from, and submit is where
 * a stale reference is reported as an error.
 */
public record ReportDraftResponse(
        UUID id,
        UUID programId,
        String title,
        String vulnerabilityInformation,
        String impact,
        String stepsToReproduce,
        String proofOfConcept,
        String remediationRecommendation,
        String targetEndpoint,
        ReportEnvironment environment,
        LocalDateTime discoveredAt,
        List<String> referenceLinks,
        Severity reportedSeverity,
        String cvssVector,
        BigDecimal cvssScore,
        UUID weaknessId,
        String suggestedWeakness,
        UUID assetId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
