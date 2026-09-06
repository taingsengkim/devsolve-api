package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportActivity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ReportMapper {

    /**
     * How much of the finding travels with a queue row. Enough to tell one
     * report from another at a glance; the whole thing is one click away.
     */
    private static final int SUMMARY_LENGTH = 220;

    /**
     * @param organization the company behind the report's program, or null when
     *                     its row is gone. A program holds its organization as
     *                     a bare id with no mapped association, so it cannot be
     *                     reached from here and has to be handed in — see
     *                     {@link ReportResponseAssembler}, which is what every
     *                     caller should go through
     */
    public ReportResponse toResponse(Report report, Organization organization) {
        return new ReportResponse(
                report.getId(),
                shortReference(report.getId()),
                report.getProgram().getId(),
                report.getReporter().getId(),
                toResearcherSummary(report.getReporter()),
                toProgramSummary(report.getProgram(), organization),
                report.getTitle(),
                summarise(report.getVulnerabilityInformation()),
                report.getVulnerabilityInformation(),
                report.getImpact(),
                report.getStepsToReproduce(),
                report.getProofOfConcept(),
                report.getRemediationRecommendation(),
                report.getTargetEndpoint(),
                report.getEnvironment(),
                report.getDiscoveredAt(),
                report.getReferenceLinks() == null
                        ? List.of()
                        : List.copyOf(report.getReferenceLinks()),
                report.getReportedSeverity(),
                report.getCvssVector(),
                report.getCvssScore(),
                report.getTriageSeverity(),
                report.getSeverity(),
                toWeaknessSummary(report),
                report.getSuggestedWeakness(),
                toAssetSummary(report),
                report.getState(),
                report.getProgram().getEngagementType(),
                report.getDisclosureStatus(),
                report.getDuplicateOf() == null
                        ? null
                        : report.getDuplicateOf().getId(),
                report.getTriagedBy() == null
                        ? null
                        : report.getTriagedBy().getId(),
                latestDispute(report),
                isDisputed(report),
                report.getAttachments().stream()
                        .map(this::toAttachmentSummary)
                        .toList(),
                report.getRewards().stream()
                        .map(this::toRewardSummary)
                        .toList(),
                report.getRetests().stream()
                        .map(this::toRetestSummary)
                        .toList(),
                report.getSubmittedAt(),
                report.getTriagedAt(),
                report.getFirstRespondedAt(),
                report.getResolvedAt(),
                report.getReputationPoints(),
                report.getReputationAwardedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    /**
     * {@code RPT-} and the first eight characters of the id, upper case.
     *
     * <p>Derived rather than a stored sequence. A per-company counter is what a
     * triager would rather quote, but it needs a row to hand out numbers and a
     * decision about what happens when two companies collide; this is stable,
     * needs no column, and reads back to a real id.
     */
    private String shortReference(java.util.UUID id) {
        return "RPT-" + id.toString().substring(0, 8).toUpperCase();
    }

    /**
     * The opening of the finding on one line.
     *
     * <p>Whitespace is collapsed because the source is markdown, and a summary
     * that starts on a fenced code block renders as an empty row.
     */
    private String summarise(String body) {
        if (body == null) {
            return null;
        }
        String collapsed = body.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        if (collapsed.length() <= SUMMARY_LENGTH) {
            return collapsed;
        }
        String cut = collapsed.substring(0, SUMMARY_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > SUMMARY_LENGTH - 40) {
            cut = cut.substring(0, lastSpace);
        }
        return cut + "…";
    }

    private ReportResponse.ResearcherSummary toResearcherSummary(
            UserProfile reporter
    ) {
        if (reporter == null) {
            return null;
        }
        return new ReportResponse.ResearcherSummary(
                reporter.getId(),
                reporter.getUsername(),
                reporter.getFullName(),
                reporter.getEmail(),
                reporter.getAvatarUrl(),
                reporter.getReputation(),
                reporter.getTotalReports(),
                reporter.getValidReports(),
                reporter.getCountry()
        );
    }

    private ReportResponse.ProgramSummary toProgramSummary(
            Program program,
            Organization organization
    ) {
        if (program == null) {
            return null;
        }
        return new ReportResponse.ProgramSummary(
                program.getId(),
                program.getName(),
                program.getHandle(),
                program.getOrganizationId(),
                organization == null ? null : organization.getName(),
                organization == null ? null : organization.getLogoUrl()
        );
    }

    /**
     * Open right now, not ever raised. A settled disagreement is not a dispute,
     * and a queue that coloured those rows would keep flagging findings both
     * sides stopped arguing about weeks ago.
     */
    private boolean isDisputed(Report report) {
        return report.getDisputes().stream().anyMatch(dispute ->
                dispute.getStatus() == DisputeStatus.OPEN
                        || dispute.getStatus() == DisputeStatus.UNDER_REVIEW
                        || dispute.getStatus()
                                == DisputeStatus.AWAITING_REPORTER
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
                "/api/v1/reports/"
                        + attachment.getReport().getId()
                        + "/attachments/"
                        + attachment.getId()
                        + "/download",
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

    private ReportResponse.RetestSummary toRetestSummary(
            ReportRetest retest
    ) {
        return new ReportResponse.RetestSummary(
                retest.getId(),
                retest.getAttemptNumber(),
                retest.getEnvironment(),
                retest.getTargetEndpoint(),
                retest.getRequestedAt(),
                retest.getDueAt(),
                toActorSummary(retest.getRequestedBy()),
                retest.getRequestNotes(),
                retest.getBountyReward(),
                retest.getCompletedAt(),
                toActorSummary(retest.getCompletedBy()),
                retest.getVerdict(),
                retest.getResultNotes(),
                retest.getAttachmentIds() == null
                        ? List.of()
                        : List.copyOf(retest.getAttachmentIds())
        );
    }

    public ReportActivityResponse toActivityResponse(ReportActivity activity) {
        return new ReportActivityResponse(
                activity.getId(),
                activity.getActivityType(),
                toActorSummary(activity.getActor()),
                activity.getFromState(),
                activity.getToState(),
                activity.getSeverity(),
                activity.getDetail(),
                activity.getCreatedAt()
        );
    }

    private ReportResponse.ActorSummary toActorSummary(UserProfile actor) {
        if (actor == null) {
            return null;
        }
        return new ReportResponse.ActorSummary(
                actor.getId(),
                actor.getFullName()
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
                dispute.getRaisedBy() == null
                        ? null
                        : dispute.getRaisedBy().getId(),
                dispute.getReason(),
                dispute.getResolvedSeverity(),
                dispute.getResolvedBy() == null
                        ? null
                        : dispute.getResolvedBy().getId(),
                dispute.getResolutionNotes(),
                dispute.getCreatedAt(),
                dispute.getResolvedAt(),
                dispute.getRespondBy()
        );
    }
}
