package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A dispute as an administrator sees it in the queue: enough of the report and
 * both severity claims to decide without opening anything else.
 */
public record DisputeResponse(
        UUID id,
        UUID reportId,
        String reportTitle,
        UUID programId,
        String programName,
        UUID organizationId,
        UUID reporterId,
        Severity reportedSeverity,
        Severity triageSeverity,
        String cvssVector,
        BigDecimal cvssScore,
        DisputeStatus status,
        UUID raisedBy,
        String reason,
        Severity resolvedSeverity,
        UUID resolvedBy,
        String resolutionNotes,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
