package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class ReportMapper {

    public ReportResponse toResponse(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getProgram().getId(),
                report.getReporter().getId(),
                report.getTitle(),
                report.getVulnerabilityInformation(),
                report.getImpact(),
                report.getReportedSeverity(),
                report.getTriageSeverity(),
                report.getSeverity(),
                toWeaknessSummary(report),
                toAssetSummary(report),
                report.getState(),
                report.getDisclosureStatus(),
                report.getDuplicateOf() == null
                        ? null
                        : report.getDuplicateOf().getId(),
                report.getTriagedBy() == null
                        ? null
                        : report.getTriagedBy().getId(),
                latestDispute(report),
                report.getAttachments().stream()
                        .map(this::toAttachmentSummary)
                        .toList(),
                report.getRewards().stream()
                        .map(this::toRewardSummary)
                        .toList(),
                report.getSubmittedAt(),
                report.getTriagedAt(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private ReportResponse.WeaknessSummary toWeaknessSummary(Report report) {
        if (report.getWeakness() == null) {
            return null;
        }
        return new ReportResponse.WeaknessSummary(
                report.getWeakness().getId(),
                report.getWeakness().getCweId(),
                report.getWeakness().getName()
        );
    }

    private ReportResponse.AssetSummary toAssetSummary(Report report) {
        if (report.getAsset() == null) {
            return null;
        }
        return new ReportResponse.AssetSummary(
                report.getAsset().getId(),
                report.getAsset().getAssetType(),
                report.getAsset().getIdentifier(),
                report.getAsset().getIsInScope(),
                report.getAsset().getMaxSeverity()
        );
    }

    private ReportResponse.AttachmentSummary toAttachmentSummary(
            ReportAttachment attachment
    ) {
        return new ReportResponse.AttachmentSummary(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getFileUrl(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy().getId(),
                attachment.getCreatedAt()
        );
    }

    private ReportResponse.RewardSummary toRewardSummary(
            ReportReward reward
    ) {
        return new ReportResponse.RewardSummary(
                reward.getId(),
                reward.getAmount(),
                reward.getPoints(),
                reward.getAwardedBy().getId(),
                reward.getAwardedAt(),
                reward.getNote()
        );
    }

    private ReportResponse.DisputeSummary latestDispute(Report report) {
        return report.getDisputes().stream()
                .max(Comparator.comparing(
                        Dispute::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ))
                .map(this::toDisputeSummary)
                .orElse(null);
    }

    private ReportResponse.DisputeSummary toDisputeSummary(Dispute dispute) {
        return new ReportResponse.DisputeSummary(
                dispute.getId(),
                dispute.getStatus(),
                dispute.getResolvedSeverity(),
                dispute.getResolvedBy() == null
                        ? null
                        : dispute.getResolvedBy().getId(),
                dispute.getResolutionNotes(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt()
        );
    }
}
