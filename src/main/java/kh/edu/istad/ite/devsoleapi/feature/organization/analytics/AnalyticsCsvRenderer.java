package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.KpiSummary;

import java.util.List;
import java.util.Locale;

/**
 * The analytics response as a spreadsheet — one file with a section per chart.
 *
 * <p>Values are escaped to RFC 4180 and, separately, defanged for the
 * spreadsheet that opens them. Half of what lands in this file is written by
 * researchers and by whoever declared an asset in scope, and a cell beginning
 * {@code =} or {@code @} is a formula to Excel and Sheets.
 */
public final class AnalyticsCsvRenderer {

    private static final String NEWLINE = "\r\n";

    /**
     * What a spreadsheet reads as the start of a formula. The tab and carriage
     * return are here because some versions strip leading whitespace before
     * deciding, so a cell starting with one can smuggle the rest past this.
     */
    private static final String FORMULA_STARTERS = "=+-@\t\r";

    private AnalyticsCsvRenderer() {
    }

    public static String render(OrganizationAnalyticsResponse analytics) {
        StringBuilder csv = new StringBuilder();

        row(csv, "DevSolve organization analytics");
        row(csv, "Organization", analytics.organizationName());
        row(csv, "Organization ID", analytics.organizationId());
        row(csv, "Time range", analytics.timeRange());
        row(csv, "Program filter",
                analytics.filterProgramId() == null
                        ? "All programs"
                        : analytics.filterProgramId());
        row(csv, "Generated at", analytics.generatedAt());
        blank(csv);

        KpiSummary kpi = analytics.kpiSummary();
        row(csv, "Summary", "Value", "Change %", "Trend");
        row(csv, "Total reports",
                kpi.totalReports().value(),
                kpi.totalReports().changePercentage(),
                kpi.totalReports().trend());
        row(csv, "Accepted reports",
                kpi.acceptedReports().value(),
                kpi.acceptedReports().changePercentage(),
                kpi.acceptedReports().trend());
        row(csv, "Acceptance rate %", kpi.acceptedReports().acceptanceRate());
        row(csv, "Rejected reports",
                kpi.rejectedReports().value(),
                kpi.rejectedReports().changePercentage(),
                kpi.rejectedReports().trend());
        row(csv, "Rejection rate %", kpi.rejectedReports().rejectionRate());
        row(csv, "Bounties paid (" + kpi.totalBountiesPaid().currency() + ")",
                kpi.totalBountiesPaid().amount(),
                kpi.totalBountiesPaid().changePercentage(),
                kpi.totalBountiesPaid().trend());
        row(csv, "Reputation points awarded", kpi.reputationPointsAwarded());
        row(csv, "Active researchers",
                kpi.activeResearchers().value(),
                kpi.activeResearchers().changePercentage(),
                kpi.activeResearchers().trend());
        row(csv, "Mean time to triage (hours)",
                kpi.slaMetrics().meanTimeToTriageHours());
        row(csv, "Mean time to resolve (days)",
                kpi.slaMetrics().meanTimeToResolveDays());
        row(csv, "Triage SLA compliance %",
                kpi.slaMetrics().slaCompliancePercentage());
        row(csv, "Triage SLA target (hours)",
                kpi.slaMetrics().triageTargetHours());
        blank(csv);

        row(csv, "Submission trend");
        row(csv, "Period", "Label", "Submitted", "Accepted", "Resolved",
                "Rejected", "Bounty paid");
        analytics.submissionTrend().forEach(point -> row(csv,
                point.period(),
                point.label(),
                point.submitted(),
                point.accepted(),
                point.resolved(),
                point.rejected(),
                point.bountyPaid()));
        blank(csv);

        row(csv, "Severity distribution");
        row(csv, "Severity", "Reports", "Share %", "Average bounty");
        severityRows(csv, analytics.severityDistribution());
        blank(csv);

        row(csv, "Top vulnerability categories");
        row(csv, "CWE", "Name", "Reports", "Share %", "Critical");
        analytics.topVulnerabilityCategories().forEach(category -> row(csv,
                category.cweId(),
                category.name(),
                category.count(),
                category.percentage(),
                category.criticalCount()));
        blank(csv);

        row(csv, "Top targeted assets");
        row(csv, "Asset", "Type", "Reports", "Critical", "High",
                "Total bounty");
        analytics.topTargetedAssets().forEach(asset -> row(csv,
                asset.assetTarget(),
                asset.assetType(),
                asset.totalReports(),
                asset.criticalCount(),
                asset.highCount(),
                asset.totalBounty()));
        blank(csv);

        row(csv, "Top researchers");
        row(csv, "Rank", "Username", "Display name", "Valid reports",
                "Critical reports", "Bounties earned", "Reputation earned");
        analytics.topResearchers().forEach(researcher -> row(csv,
                researcher.rank(),
                researcher.username(),
                researcher.displayName(),
                researcher.validReports(),
                researcher.criticalReports(),
                researcher.totalBountiesEarned(),
                researcher.reputationEarned()));

        return csv.toString();
    }

    private static void severityRows(
            StringBuilder csv,
            OrganizationAnalyticsResponse.SeverityDistribution distribution
    ) {
        severityRow(csv, "Critical", distribution.critical());
        severityRow(csv, "High", distribution.high());
        severityRow(csv, "Medium", distribution.medium());
        severityRow(csv, "Low", distribution.low());
        severityRow(csv, "None", distribution.none());
    }

    private static void severityRow(
            StringBuilder csv,
            String label,
            OrganizationAnalyticsResponse.SeverityBand band
    ) {
        row(csv, label, band.count(), band.percentage(), band.avgBounty());
    }

    private static void blank(StringBuilder csv) {
        csv.append(NEWLINE);
    }

    private static void row(StringBuilder csv, Object... cells) {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(cell(cells[index]));
        }
        csv.append(NEWLINE);
    }

    /**
     * One cell: neutralised, then quoted if it needs to be. Numbers are
     * written bare; a null becomes an empty cell, not the word "null".
     */
    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return value.toString();
        }

        String text = value.toString();
        if (!text.isEmpty()
                && FORMULA_STARTERS.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') < 0
                && text.indexOf('"') < 0
                && text.indexOf('\n') < 0
                && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    /** Reduced to characters every filesystem accepts. */
    public static String fileName(OrganizationAnalyticsResponse analytics) {
        String slug = analytics.organizationName() == null
                ? "organization"
                : analytics.organizationName()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            slug = "organization";
        }
        return String.join(
                "-",
                List.of("devsolve-analytics", slug, analytics.timeRange())
        ) + ".csv";
    }
}
