package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.ResearcherStanding;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse.SubmissionTrendPoint;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Turning six result sets into the page, which is where the shape a client
 * relies on is decided: no null sections, every severity band present, and a
 * trend with its quiet periods still in it.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationAnalyticsCacheTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private OrganizationAnalyticsRepository analyticsRepository;

    /**
     * The empty-organization contract. Every one of these is a field the
     * dashboard reads without guarding, so a null or a missing band is a
     * blank page rather than a page of zeros.
     */
    @Test
    void anOrganizationWithNoReportsGetsZerosRatherThanNulls() {
        stubSummaries(summary(0, 0, 0, 0, 0, "0", 0, 0, 0, 0, 0, null));

        OrganizationAnalyticsResponse analytics = load(
                AnalyticsTimeRange.LAST_6_MONTHS
        );

        assertEquals(0L, analytics.kpiSummary().totalReports().value());
        assertEquals(
                new BigDecimal("0.0"),
                analytics.kpiSummary().acceptedReports().acceptanceRate()
        );
        assertEquals(
                new BigDecimal("0.00"),
                analytics.kpiSummary().totalBountiesPaid().amount()
        );
        assertEquals(
                OrganizationAnalyticsCache.BOUNTY_CURRENCY,
                analytics.kpiSummary().totalBountiesPaid().currency()
        );
        assertEquals(
                new BigDecimal("0.0"),
                analytics.kpiSummary().slaMetrics().slaCompliancePercentage()
        );
        assertEquals(
                OrganizationAnalyticsCache.TRIAGE_TARGET_HOURS,
                analytics.kpiSummary().slaMetrics().triageTargetHours()
        );
        assertEquals(List.of(), analytics.topVulnerabilityCategories());
        assertEquals(List.of(), analytics.topTargetedAssets());
        assertEquals(List.of(), analytics.topResearchers());

        // Five bands, always. A donut chart reads all of them.
        assertNotNull(analytics.severityDistribution().critical());
        assertNotNull(analytics.severityDistribution().high());
        assertNotNull(analytics.severityDistribution().medium());
        assertNotNull(analytics.severityDistribution().low());
        assertNotNull(analytics.severityDistribution().none());
        assertEquals(0L, analytics.severityDistribution().critical().count());
    }

    /**
     * Six months back plus the current month is seven points, and a bounded
     * range draws them whether or not anything was submitted.
     */
    @Test
    void aBoundedRangeDrawsEveryBucketInTheWindow() {
        stubSummaries(summary(0, 0, 0, 0, 0, "0", 0, 0, 0, 0, 0, null));

        List<SubmissionTrendPoint> trend =
                load(AnalyticsTimeRange.LAST_6_MONTHS).submissionTrend();

        assertEquals(7, trend.size());
        assertTrue(trend.stream().allMatch(point -> point.submitted() == 0L));
        assertTrue(trend.stream().allMatch(point ->
                new BigDecimal("0.00").equals(point.bountyPaid())
        ));
    }

    /**
     * A month nothing was submitted in is a fact about the month, not a gap
     * in the series — dropping it would draw the chart with the quiet period
     * closed up.
     */
    @Test
    void aQuietPeriodStaysInTheSeriesAsAZero() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        stubSummaries(summary(4, 3, 1, 2, 2, "500", 60, 5, 2, 4, 3, null));
        when(analyticsRepository.findSubmissionTrend(
                any(), any(), any(), any(), eq("month")
        )).thenReturn(List.of(
                new StubTrendBucket(
                        thisMonth.toString(), 4, 3, 2, 1, new BigDecimal("500")
                )
        ));

        List<SubmissionTrendPoint> trend =
                load(AnalyticsTimeRange.LAST_6_MONTHS).submissionTrend();

        assertEquals(7, trend.size());
        assertEquals(0L, trend.getFirst().submitted());
        SubmissionTrendPoint latest = trend.getLast();
        assertEquals(4L, latest.submitted());
        assertEquals(3L, latest.accepted());
        assertEquals(2L, latest.resolved());
        assertEquals(1L, latest.rejected());
        assertEquals(new BigDecimal("500.00"), latest.bountyPaid());
    }

    @Test
    void tilesAreComparedAgainstThePrecedingWindow() {
        when(analyticsRepository.summarize(
                any(), any(), any(), any(), any(), anyInt()
        ))
                .thenReturn(summary(
                        248, 168, 45, 120, 82, "54250.00", 4850, 14.5, 11.2,
                        200, 189, null
                ))
                .thenReturn(summary(
                        217, 155, 47, 100, 71, "48437.50", 4000, 20.0, 15.0,
                        180, 150, null
                ));

        var kpi = load(AnalyticsTimeRange.LAST_6_MONTHS).kpiSummary();

        assertEquals(248L, kpi.totalReports().value());
        assertEquals(
                new BigDecimal("14.3"),
                kpi.totalReports().changePercentage()
        );
        assertEquals(
                AnalyticsFigures.TREND_UP,
                kpi.totalReports().trend()
        );
        assertEquals(
                new BigDecimal("67.7"),
                kpi.acceptedReports().acceptanceRate()
        );
        assertEquals(
                new BigDecimal("18.1"),
                kpi.rejectedReports().rejectionRate()
        );
        assertEquals(
                AnalyticsFigures.TREND_DOWN,
                kpi.rejectedReports().trend()
        );
        assertEquals(
                new BigDecimal("12.0"),
                kpi.totalBountiesPaid().changePercentage()
        );
        assertEquals(4850L, kpi.reputationPointsAwarded());
        assertEquals(
                new BigDecimal("14.5"),
                kpi.slaMetrics().meanTimeToTriageHours()
        );
        assertEquals(
                new BigDecimal("11.2"),
                kpi.slaMetrics().meanTimeToResolveDays()
        );
        // 189 of the 200 reports whose target has been settled were triaged
        // in time. The 48 still inside their target are in neither figure.
        assertEquals(
                new BigDecimal("94.5"),
                kpi.slaMetrics().slaCompliancePercentage()
        );
    }

    /**
     * All of history has no window behind it, so the second query is not run
     * and every tile says it has nothing to compare against.
     */
    @Test
    void allTimeRunsNoComparisonQueryAndReportsNoChange() {
        LocalDate firstReport = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        when(analyticsRepository.summarize(
                any(), any(), any(), any(), any(), anyInt()
        )).thenReturn(summary(
                90, 60, 20, 40, 30, "9000.00", 1200, 8.0, 6.0, 80, 80,
                firstReport.toString()
        ));

        OrganizationAnalyticsResponse analytics =
                load(AnalyticsTimeRange.ALL_TIME);

        verify(analyticsRepository, times(1)).summarize(
                any(), any(), any(), any(), any(), anyInt()
        );
        assertNull(
                analytics.kpiSummary().totalReports().changePercentage()
        );
        assertEquals(
                AnalyticsFigures.TREND_FLAT,
                analytics.kpiSummary().totalReports().trend()
        );
        // Three months: the month of the first report, and the two since.
        assertEquals(3, analytics.submissionTrend().size());
        assertEquals("all", analytics.timeRange());
    }

    @Test
    void allTimeWithNoReportsAtAllHasNothingToDraw() {
        when(analyticsRepository.summarize(
                any(), any(), any(), any(), any(), anyInt()
        )).thenReturn(summary(0, 0, 0, 0, 0, "0", 0, 0, 0, 0, 0, null));

        assertEquals(
                List.of(),
                load(AnalyticsTimeRange.ALL_TIME).submissionTrend()
        );
    }

    @Test
    void severityBandsCarryTheirShareOfEveryReportInTheWindow() {
        stubSummaries(summary(
                100, 60, 20, 50, 30, "5000.00", 900, 10, 5, 90, 85, null
        ));
        when(analyticsRepository.findSeverityDistribution(
                any(), any(), any(), any()
        )).thenReturn(List.of(
                new StubSeverityTally("critical", 18, new BigDecimal("2500")),
                new StubSeverityTally("high", 52, new BigDecimal("1000")),
                new StubSeverityTally("medium", 30, new BigDecimal("350"))
        ));

        var distribution =
                load(AnalyticsTimeRange.LAST_6_MONTHS).severityDistribution();

        assertEquals(18L, distribution.critical().count());
        assertEquals(new BigDecimal("18.0"), distribution.critical().percentage());
        assertEquals(new BigDecimal("2500.00"), distribution.critical().avgBounty());
        assertEquals(52L, distribution.high().count());
        assertEquals(new BigDecimal("52.0"), distribution.high().percentage());
        // Bands nothing landed in still appear, at zero.
        assertEquals(0L, distribution.low().count());
        assertEquals(new BigDecimal("0.00"), distribution.low().avgBounty());
    }

    @Test
    void researchersAreRankedInTheOrderTheQueryReturnedThem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubSummaries(summary(
                40, 30, 5, 20, 2, "20000.00", 900, 10, 5, 35, 30, null
        ));
        when(analyticsRepository.findTopResearchers(
                any(), any(), any(), any(), anyInt()
        )).thenReturn(List.of(
                new StubResearcherTally(
                        first, "0xsec_hunter", "Alex Rivera",
                        "https://example.test/a.png",
                        18, 4, new BigDecimal("14500"), 720
                ),
                new StubResearcherTally(
                        second, "nullbyte", "Sam Ng", null,
                        9, 1, new BigDecimal("3000"), 260
                )
        ));

        List<ResearcherStanding> researchers =
                load(AnalyticsTimeRange.LAST_6_MONTHS).topResearchers();

        assertEquals(1, researchers.getFirst().rank());
        assertEquals(first, researchers.getFirst().userId());
        assertEquals("0xsec_hunter", researchers.getFirst().username());
        assertEquals(
                new BigDecimal("14500.00"),
                researchers.getFirst().totalBountiesEarned()
        );
        assertEquals(2, researchers.getLast().rank());
        assertEquals(second, researchers.getLast().userId());
        assertNull(researchers.getLast().avatarUrl());
    }

    /**
     * Native results arrive as the database's lower-case text. A value this
     * build has never heard of means the schema ran ahead of the application,
     * and a dashboard that failed over an unknown label would be worse than
     * one that says OTHER.
     */
    @Test
    void assetTypesComeBackAsEnumsAndAnUnknownOneFallsToOther() {
        stubSummaries(summary(
                20, 15, 2, 10, 4, "3000.00", 200, 6, 3, 18, 16, null
        ));
        when(analyticsRepository.findTopAssets(
                any(), any(), any(), any(), anyInt()
        )).thenReturn(List.of(
                new StubAssetTally(
                        "api.acme.test", "api", 56, 7, 18,
                        new BigDecimal("18500")
                ),
                new StubAssetTally(
                        "Acme for iOS", "mobile_app", 22, 1, 5,
                        new BigDecimal("4500")
                ),
                new StubAssetTally(
                        "satellite-uplink", "orbital", 3, 0, 1, null
                )
        ));

        var assets = load(AnalyticsTimeRange.LAST_6_MONTHS).topTargetedAssets();

        assertEquals(AssetType.API, assets.get(0).assetType());
        assertEquals(new BigDecimal("18500.00"), assets.get(0).totalBounty());
        assertEquals(AssetType.MOBILE_APP, assets.get(1).assetType());
        assertEquals(AssetType.OTHER, assets.get(2).assetType());
        assertEquals(new BigDecimal("0.00"), assets.get(2).totalBounty());
    }

    /**
     * A report triage has not classified belongs to no CWE, so these shares
     * are of every report in the window and are not expected to sum to 100.
     */
    @Test
    void weaknessSharesAreOfEveryReportNotOfTheClassifiedOnes() {
        stubSummaries(summary(
                248, 168, 45, 120, 82, "54250.00", 4850, 14.5, 11.2, 200, 189,
                null
        ));
        when(analyticsRepository.findTopWeaknesses(
                any(), any(), any(), any(), anyInt()
        )).thenReturn(List.of(
                new StubWeaknessTally(
                        "CWE-284", "Improper Access Control", 48, 8
                )
        ));

        var categories =
                load(AnalyticsTimeRange.LAST_6_MONTHS).topVulnerabilityCategories();

        assertEquals("CWE-284", categories.getFirst().cweId());
        assertEquals(48L, categories.getFirst().count());
        assertEquals(
                new BigDecimal("19.4"),
                categories.getFirst().percentage()
        );
        assertEquals(8L, categories.getFirst().criticalCount());
    }

    @Test
    void aProgramFilterIsPassedThroughToEveryQuery() {
        UUID programId = UUID.randomUUID();
        stubSummaries(summary(0, 0, 0, 0, 0, "0", 0, 0, 0, 0, 0, null));

        new OrganizationAnalyticsCache(analyticsRepository).load(
                ORGANIZATION_ID,
                "Acme Corp",
                AnalyticsTimeRange.LAST_30_DAYS,
                programId
        );

        verify(analyticsRepository).findTopAssets(
                eq(ORGANIZATION_ID), eq(programId), any(), any(), anyInt()
        );
        verify(analyticsRepository).findSubmissionTrend(
                eq(ORGANIZATION_ID), eq(programId), any(), any(), eq("day")
        );
    }

    private OrganizationAnalyticsResponse load(AnalyticsTimeRange range) {
        return new OrganizationAnalyticsCache(analyticsRepository).load(
                ORGANIZATION_ID,
                "Acme Corp",
                range,
                null
        );
    }

    private void stubSummaries(
            OrganizationAnalyticsRepository.WindowSummary summary
    ) {
        when(analyticsRepository.summarize(
                any(), any(), any(), any(), any(), anyInt()
        )).thenReturn(summary);
    }

    private OrganizationAnalyticsRepository.WindowSummary summary(
            long total,
            long accepted,
            long rejected,
            long resolved,
            long researchers,
            String bounties,
            long reputation,
            double triageHours,
            double resolveDays,
            long slaDecided,
            long slaMet,
            String firstSubmittedOn
    ) {
        return new StubWindowSummary(
                total,
                accepted,
                rejected,
                resolved,
                researchers,
                new BigDecimal(bounties),
                reputation,
                triageHours,
                resolveDays,
                slaDecided,
                slaMet,
                firstSubmittedOn
        );
    }

    private record StubWindowSummary(
            long total,
            long accepted,
            long rejected,
            long resolved,
            long researchers,
            BigDecimal bounties,
            long reputation,
            double triageHours,
            double resolveDays,
            long slaDecided,
            long slaMet,
            String firstSubmittedOn
    ) implements OrganizationAnalyticsRepository.WindowSummary {

        @Override
        public long getTotalReports() {
            return total;
        }

        @Override
        public long getAcceptedReports() {
            return accepted;
        }

        @Override
        public long getRejectedReports() {
            return rejected;
        }

        @Override
        public long getResolvedReports() {
            return resolved;
        }

        @Override
        public long getActiveResearchers() {
            return researchers;
        }

        @Override
        public BigDecimal getBountiesPaid() {
            return bounties;
        }

        @Override
        public long getReputationPoints() {
            return reputation;
        }

        @Override
        public double getMeanTriageHours() {
            return triageHours;
        }

        @Override
        public double getMeanResolveDays() {
            return resolveDays;
        }

        @Override
        public long getSlaDecidedReports() {
            return slaDecided;
        }

        @Override
        public long getSlaMetReports() {
            return slaMet;
        }

        @Override
        public String getFirstSubmittedOn() {
            return firstSubmittedOn;
        }
    }

    private record StubTrendBucket(
            String bucketStart,
            long submitted,
            long accepted,
            long resolved,
            long rejected,
            BigDecimal bountyPaid
    ) implements OrganizationAnalyticsRepository.TrendBucket {

        @Override
        public String getBucketStart() {
            return bucketStart;
        }

        @Override
        public long getSubmitted() {
            return submitted;
        }

        @Override
        public long getAccepted() {
            return accepted;
        }

        @Override
        public long getResolved() {
            return resolved;
        }

        @Override
        public long getRejected() {
            return rejected;
        }

        @Override
        public BigDecimal getBountyPaid() {
            return bountyPaid;
        }
    }

    private record StubSeverityTally(
            String severity,
            long total,
            BigDecimal averageBounty
    ) implements OrganizationAnalyticsRepository.SeverityTally {

        @Override
        public String getSeverity() {
            return severity;
        }

        @Override
        public long getTotal() {
            return total;
        }

        @Override
        public BigDecimal getAverageBounty() {
            return averageBounty;
        }
    }

    private record StubWeaknessTally(
            String cweId,
            String name,
            long total,
            long criticalCount
    ) implements OrganizationAnalyticsRepository.WeaknessTally {

        @Override
        public String getCweId() {
            return cweId;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getTotal() {
            return total;
        }

        @Override
        public long getCriticalCount() {
            return criticalCount;
        }
    }

    private record StubAssetTally(
            String assetTarget,
            String assetType,
            long totalReports,
            long criticalCount,
            long highCount,
            BigDecimal totalBounty
    ) implements OrganizationAnalyticsRepository.AssetTally {

        @Override
        public String getAssetTarget() {
            return assetTarget;
        }

        @Override
        public String getAssetType() {
            return assetType;
        }

        @Override
        public long getTotalReports() {
            return totalReports;
        }

        @Override
        public long getCriticalCount() {
            return criticalCount;
        }

        @Override
        public long getHighCount() {
            return highCount;
        }

        @Override
        public BigDecimal getTotalBounty() {
            return totalBounty;
        }
    }

    private record StubResearcherTally(
            UUID userId,
            String username,
            String displayName,
            String avatarUrl,
            long validReports,
            long criticalReports,
            BigDecimal totalBountiesEarned,
            long reputationEarned
    ) implements OrganizationAnalyticsRepository.ResearcherTally {

        @Override
        public UUID getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getAvatarUrl() {
            return avatarUrl;
        }

        @Override
        public long getValidReports() {
            return validReports;
        }

        @Override
        public long getCriticalReports() {
            return criticalReports;
        }

        @Override
        public BigDecimal getTotalBountiesEarned() {
            return totalBountiesEarned;
        }

        @Override
        public long getReputationEarned() {
            return reputationEarned;
        }
    }
}
