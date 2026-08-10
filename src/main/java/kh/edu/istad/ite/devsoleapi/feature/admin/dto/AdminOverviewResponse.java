package kh.edu.istad.ite.devsoleapi.feature.admin.dto;

import java.time.Instant;

public record AdminOverviewResponse(
        Instant generatedAt,
        UserOverview users,
        OrganizationOverview organizations,
        ProgramOverview programs,
        ReportOverview reports,
        ModerationOverview moderation
) {

    public record UserOverview(
            long total,
            long active,
            long suspended,
            long removed
    ) {
    }

    public record OrganizationOverview(
            long total,
            long active,
            long pendingReview,
            long rejected
    ) {
    }

    public record ProgramOverview(
            long total,
            long draft,
            long active,
            long paused,
            long closed,
            long pendingReview
    ) {
    }

    public record ReportOverview(
            long total,
            long open,
            long newReports,
            long triaging,
            long needsMoreInfo,
            long validConfirmed,
            long resolved,
            long rejected,
            long duplicate
    ) {
    }

    public record ModerationOverview(
            long totalPending,
            long organizations,
            long programs,
            long problems,
            long showcases,
            long solutions,
            long contentFlags
    ) {
    }
}
