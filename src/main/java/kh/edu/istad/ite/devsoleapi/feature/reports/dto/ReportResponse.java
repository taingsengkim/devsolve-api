package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * @param reputationPoints what resolving this finding earned its reporter,
 *                         priced by severity by the platform rather than by
 *                         the organization. Null until the report is resolved,
 *                         and on reports resolved before reputation was paid on
 *                         resolution. Zero on an informational finding, which
 *                         is credited but does not move the leaderboard.
 */
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
        List<RetestSummary> retestHistory,
        LocalDateTime submittedAt,
        LocalDateTime triagedAt,
        LocalDateTime resolvedAt,
        Integer reputationPoints,
        LocalDateTime reputationAwardedAt,
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

    /**
     * One round of fix verification, oldest attempt first.
     *
     * <p>{@code completedAt} is what says whether the attempt is still open;
     * an attempt closed without a {@code verdict} is one triage moved the
     * report on from, or one whose {@code dueAt} passed unanswered.
     *
     * @param dueAt when the researcher's window to answer runs out. Null on
     *              attempts that predate the window.
     */
    public record RetestSummary(
            UUID id,
            int attemptNumber,
            ReportEnvironment environment,
            String targetEndpoint,
            LocalDateTime requestedAt,
            LocalDateTime dueAt,
            ActorSummary requestedBy,
            String requestNotes,
            BigDecimal bountyReward,
            LocalDateTime completedAt,
            ActorSummary completedBy,
            RetestVerdict verdict,
            String resultNotes,
            List<UUID> attachmentIds
    ) {
    }

    /**
     * Who did something, named. The rest of this response carries bare ids,
     * but a retest history is read as a chronology of people acting on the
     * report, and resolving every id to a name client-side to render it is a
     * request per row.
     */
    public record ActorSummary(
            UUID id,
            String name
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
