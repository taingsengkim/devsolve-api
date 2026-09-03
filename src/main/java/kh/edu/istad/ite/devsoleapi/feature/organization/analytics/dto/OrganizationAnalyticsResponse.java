package kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the company analytics dashboard draws, in one response.
 *
 * <p>One endpoint rather than eight because the page shows one window: a
 * leaderboard fetched a second after the KPI tiles would be scoped to a window
 * a second wider, and the two would not add up.
 *
 * <p>Nothing is ever null for want of data. An organization with no reports
 * gets zeros, empty lists and every severity band present.
 *
 * @param filterProgramId the program the figures were narrowed to, or null for
 *                        every program the organization runs
 * @param generatedAt     when these figures were computed, which is not when
 *                        they were served — the response is cached briefly
 */
public record OrganizationAnalyticsResponse(
        UUID organizationId,
        String organizationName,
        String timeRange,
        UUID filterProgramId,
        Instant generatedAt,
        KpiSummary kpiSummary,
        List<SubmissionTrendPoint> submissionTrend,
        SeverityDistribution severityDistribution,
        List<VulnerabilityCategory> topVulnerabilityCategories,
        List<TargetedAsset> topTargetedAssets,
        List<ResearcherStanding> topResearchers
) {

    /**
     * @param reputationPointsAwarded read from the stamp on the report rather
     *                                than recomputed from severity, so it is
     *                                what was actually awarded even after a
     *                                dispute moved the severity afterwards
     */
    public record KpiSummary(
            CountMetric totalReports,
            AcceptedMetric acceptedReports,
            RejectedMetric rejectedReports,
            BountyMetric totalBountiesPaid,
            long reputationPointsAwarded,
            CountMetric activeResearchers,
            SlaMetrics slaMetrics
    ) {
    }

    /**
     * A count against the same count in the window before it.
     *
     * @param changePercentage null when there is nothing to compare against:
     *                         {@code timeRange=all} has no preceding window,
     *                         and growth from zero has no percentage. Zero
     *                         against zero is 0, not null. Render null as "—",
     *                         not as 0%.
     * @param trend            {@code up}, {@code down} or {@code flat}. Set
     *                         even where {@code changePercentage} is null.
     */
    public record CountMetric(
            long value,
            BigDecimal changePercentage,
            String trend
    ) {
    }

    /**
     * @param acceptanceRate accepted as a percentage of every report submitted
     *                       in the window. Accepted means confirmed, under
     *                       retest, or resolved; a duplicate counts towards
     *                       neither rate.
     */
    public record AcceptedMetric(
            long value,
            BigDecimal acceptanceRate,
            BigDecimal changePercentage,
            String trend
    ) {
    }

    public record RejectedMetric(
            long value,
            BigDecimal rejectionRate,
            BigDecimal changePercentage,
            String trend
    ) {
    }

    /**
     * @param currency the unit every payout on this platform is recorded in.
     *                 Rewards carry an amount and no currency, so this is a
     *                 constant rather than a column.
     */
    public record BountyMetric(
            BigDecimal amount,
            String currency,
            BigDecimal changePercentage,
            String trend
    ) {
    }

    /**
     * @param slaCompliancePercentage share of reports triaged inside
     *                                {@code triageTargetHours}. A report still
     *                                untriaged past the target counts against
     *                                it; one still inside the target is left
     *                                out, having missed nothing yet.
     */
    public record SlaMetrics(
            BigDecimal meanTimeToTriageHours,
            BigDecimal meanTimeToResolveDays,
            BigDecimal slaCompliancePercentage,
            int triageTargetHours
    ) {
    }

    /**
     * One point on the inflow chart. Every bucket in the window is present,
     * including the ones nothing was submitted in.
     *
     * @param period     sortable key: {@code 2026-09}, {@code 2026-W36} or
     *                   {@code 2026-09-03}, depending on the range
     * @param bountyPaid what has been paid for the findings submitted in this
     *                   period, not what was paid out during it
     */
    public record SubmissionTrendPoint(
            String period,
            String label,
            long submitted,
            long accepted,
            long resolved,
            long rejected,
            BigDecimal bountyPaid
    ) {
    }

    /** All five bands, always, so a donut chart has nothing to guard. */
    public record SeverityDistribution(
            SeverityBand critical,
            SeverityBand high,
            SeverityBand medium,
            SeverityBand low,
            SeverityBand none
    ) {
    }

    /**
     * @param percentage share of every report in the window, including the
     *                   ones nobody has assessed yet
     * @param avgBounty  the average payout among the reports in this band that
     *                   were actually paid. Unpaid reports are left out rather
     *                   than averaged in as zero.
     */
    public record SeverityBand(
            long count,
            BigDecimal percentage,
            BigDecimal avgBounty
    ) {
    }

    /**
     * @param percentage share of every report in the window. These do not sum
     *                   to 100: an unclassified report belongs to no CWE and
     *                   is counted in the denominator only.
     */
    public record VulnerabilityCategory(
            String cweId,
            String name,
            long count,
            BigDecimal percentage,
            long criticalCount
    ) {
    }

    /**
     * @param assetTarget the identifier as the program declared it. Grouped by
     *                    identifier and type rather than by asset row, so one
     *                    host in scope on three programs reads as one target.
     */
    public record TargetedAsset(
            String assetTarget,
            AssetType assetType,
            long totalReports,
            long criticalCount,
            long highCount,
            BigDecimal totalBounty
    ) {
    }

    /**
     * @param rank         position in this list, 1-based — a ranking of the
     *                     organization's own partners over this window, not a
     *                     place on the platform leaderboard
     * @param validReports accepted findings only
     */
    public record ResearcherStanding(
            UUID userId,
            String username,
            String displayName,
            String avatarUrl,
            int rank,
            long validReports,
            long criticalReports,
            BigDecimal totalBountiesEarned,
            long reputationEarned
    ) {
    }
}
