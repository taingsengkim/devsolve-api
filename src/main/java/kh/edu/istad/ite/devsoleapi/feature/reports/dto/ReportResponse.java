package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID programId,
        UUID reporterId,
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
        Severity triageSeverity,
        Severity severity,
        WeaknessSummary weakness,
        AssetSummary asset,
        ReportState state,
        DisclosureStatus disclosureStatus,
        UUID duplicateOfId,
        UUID triagedBy,
        DisputeSummary dispute,
        List<AttachmentSummary> attachments,
        List<RewardSummary> rewards,
        LocalDateTime submittedAt,
        LocalDateTime triagedAt,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record WeaknessSummary(
            UUID id,
            String cweId,
            String name
    ) {
    }

    public record AssetSummary(
            UUID id,
            AssetType assetType,
            String identifier,
            Boolean isInScope,
            Severity maxSeverity
    ) {
    }

    public record AttachmentSummary(
            UUID id,
            String fileName,
            String downloadUrl,
            String mimeType,
            Long sizeBytes,
            UUID uploadedBy,
            LocalDateTime createdAt
    ) {
    }

    public record RewardSummary(
            UUID id,
            BigDecimal amount,
            Integer points,
            UUID awardedBy,
            LocalDateTime awardedAt,
            String note
    ) {
    }

    public record DisputeSummary(
            UUID id,
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
}
