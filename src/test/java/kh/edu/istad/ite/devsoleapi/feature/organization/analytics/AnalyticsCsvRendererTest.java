package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.AcceptedMetric;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.BountyMetric;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.CountMetric;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.KpiSummary;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.RejectedMetric;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.ResearcherStanding;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.SeverityBand;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.SeverityDistribution;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.SlaMetrics;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.SubmissionTrendPoint;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.TargetedAsset;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.VulnerabilityCategory;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsCsvRendererTest {

    @Test
    void everySectionOfThePageIsInTheFile() {
        String csv = AnalyticsCsvRenderer.render(analytics(
                "Acme Corp",
                "api.acme.test",
                "0xsec_hunter"
        ));

        assertTrue(csv.contains("Total reports,248"));
        assertTrue(csv.contains("Submission trend"));
        assertTrue(csv.contains("2026-03,Mar 2026,35,24,20,6,6500.00"));
        assertTrue(csv.contains("Severity distribution"));
        assertTrue(csv.contains("Critical,18,7.3,2500.00"));
        assertTrue(csv.contains("Top vulnerability categories"));
        assertTrue(csv.contains("CWE-284"));
        assertTrue(csv.contains("Top targeted assets"));
        assertTrue(csv.contains("api.acme.test,API,56,7,18,18500.00"));
        assertTrue(csv.contains("Top researchers"));
        assertTrue(csv.contains("1,0xsec_hunter,Alex Rivera,18,4,14500.00,720"));
    }

    /**
     * The export lands in Excel, and half of what is in it was typed by a
     * researcher or by whoever declared an asset in scope. A cell that starts
     * a formula is neutralised before it gets there.
     */
    @Test
    void aCellThatWouldBeAFormulaIsNeutralised() {
        String csv = AnalyticsCsvRenderer.render(analytics(
                "Acme Corp",
                "=HYPERLINK(\"http://evil.test\",\"click\")",
                "@SUM(A1:A9)"
        ));

        assertFalse(csv.contains("\n=HYPERLINK"));
        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("'@SUM(A1:A9)"));
    }

    /**
     * A comma in an asset name would otherwise shift every column after it.
     */
    @Test
    void commasAndQuotesAreQuotedRatherThanShiftingColumns() {
        String csv = AnalyticsCsvRenderer.render(analytics(
                "Acme Corp",
                "app.acme.test, staging",
                "the \"real\" hunter"
        ));

        assertTrue(csv.contains("\"app.acme.test, staging\""));
        assertTrue(csv.contains("\"the \"\"real\"\" hunter\""));
    }

    /**
     * A tile with nothing to compare against carries a null change. It has to
     * come out as an empty cell — the word "null" in a spreadsheet is worse
     * than a blank.
     */
    @Test
    void aMissingChangePercentageIsAnEmptyCellNotTheWordNull() {
        OrganizationAnalyticsResponse analytics = analytics(
                "Acme Corp", "api.acme.test", "hunter"
        );
        String csv = AnalyticsCsvRenderer.render(new OrganizationAnalyticsResponse(
                analytics.organizationId(),
                analytics.organizationName(),
                "all",
                null,
                analytics.generatedAt(),
                new KpiSummary(
                        new CountMetric(248L, null, AnalyticsFigures.TREND_FLAT),
                        analytics.kpiSummary().acceptedReports(),
                        analytics.kpiSummary().rejectedReports(),
                        analytics.kpiSummary().totalBountiesPaid(),
                        analytics.kpiSummary().reputationPointsAwarded(),
                        analytics.kpiSummary().activeResearchers(),
                        analytics.kpiSummary().slaMetrics()
                ),
                analytics.submissionTrend(),
                analytics.severityDistribution(),
                analytics.topVulnerabilityCategories(),
                analytics.topTargetedAssets(),
                analytics.topResearchers()
        ));

        assertTrue(csv.contains("Total reports,248,,flat"));
        assertFalse(csv.contains("null"));
    }

    @Test
    void theFileNameNamesTheCompanyAndTheWindow() {
        assertEquals(
                "devsolve-analytics-acme-corp-6m.csv",
                AnalyticsCsvRenderer.fileName(
                        analytics("Acme Corp", "api.acme.test", "hunter")
                )
        );
    }

    /**
     * The organization named itself, so the filename cannot assume the name
     * survives a filesystem.
     */
    @Test
    void aCompanyNameThatIsAllPunctuationStillProducesAFileName() {
        assertEquals(
                "devsolve-analytics-organization-6m.csv",
                AnalyticsCsvRenderer.fileName(
                        analytics("///", "api.acme.test", "hunter")
                )
        );
    }

    private OrganizationAnalyticsResponse analytics(
            String organizationName,
            String assetTarget,
            String username
    ) {
        return new OrganizationAnalyticsResponse(
                UUID.randomUUID(),
                organizationName,
                "6m",
                null,
                Instant.parse("2026-09-03T10:00:00Z"),
                new KpiSummary(
                        new CountMetric(
                                248L,
                                new BigDecimal("14.2"),
                                AnalyticsFigures.TREND_UP
                        ),
                        new AcceptedMetric(
                                168L,
                                new BigDecimal("67.7"),
                                new BigDecimal("8.5"),
                                AnalyticsFigures.TREND_UP
                        ),
                        new RejectedMetric(
                                45L,
                                new BigDecimal("18.1"),
                                new BigDecimal("-4.2"),
                                AnalyticsFigures.TREND_DOWN
                        ),
                        new BountyMetric(
                                new BigDecimal("54250.00"),
                                "USD",
                                new BigDecimal("12.0"),
                                AnalyticsFigures.TREND_UP
                        ),
                        4850L,
                        new CountMetric(
                                82L,
                                new BigDecimal("15.0"),
                                AnalyticsFigures.TREND_UP
                        ),
                        new SlaMetrics(
                                new BigDecimal("14.5"),
                                new BigDecimal("11.2"),
                                new BigDecimal("94.5"),
                                24
                        )
                ),
                List.of(new SubmissionTrendPoint(
                        "2026-03",
                        "Mar 2026",
                        35L,
                        24L,
                        20L,
                        6L,
                        new BigDecimal("6500.00")
                )),
                new SeverityDistribution(
                        band(18L, "7.3", "2500.00"),
                        band(52L, "21.0", "1000.00"),
                        band(86L, "34.7", "350.00"),
                        band(72L, "29.0", "100.00"),
                        band(20L, "8.0", "0.00")
                ),
                List.of(new VulnerabilityCategory(
                        "CWE-284",
                        "Improper Access Control",
                        48L,
                        new BigDecimal("19.3"),
                        8L
                )),
                List.of(new TargetedAsset(
                        assetTarget,
                        AssetType.API,
                        56L,
                        7L,
                        18L,
                        new BigDecimal("18500.00")
                )),
                List.of(new ResearcherStanding(
                        UUID.randomUUID(),
                        username,
                        "Alex Rivera",
                        "https://example.test/a.png",
                        1,
                        18L,
                        4L,
                        new BigDecimal("14500.00"),
                        720L
                ))
        );
    }

    private SeverityBand band(long count, String share, String bounty) {
        return new SeverityBand(
                count,
                new BigDecimal(share),
                new BigDecimal(bounty)
        );
    }
}
