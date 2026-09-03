package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.AnalyticsFigures.Change;
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
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The whole analytics response, computed and cached.
 *
 * <p>A bean of its own because {@code @Cacheable} is proxy-applied: annotating
 * a method the service calls on itself would cache nothing, silently. That
 * separation earns its keep twice — it also keeps the permission check in
 * {@code OrganizationAnalyticsService} outside the cached call, so a cache hit
 * can never hand somebody figures they were not entitled to fetch.
 *
 * <p>Seven aggregates run on a miss, so the whole page is computed here rather
 * than half of it. {@code now} is read inside the cached call, so an entry is a
 * snapshot of a window up to a minute old rather than one whose end drifts.
 */
@Component
@RequiredArgsConstructor
public class OrganizationAnalyticsCache {

    /**
     * How long an organization has to make a first triage decision before the
     * report counts against its SLA. Published in the response, because a
     * compliance percentage without its target is a number, not a measure.
     */
    public static final int TRIAGE_TARGET_HOURS = 24;

    /** Every payout on this platform is recorded in one currency. */
    public static final String BOUNTY_CURRENCY = "USD";

    /**
     * The most points the trend will ever carry; past that it keeps the most
     * recent buckets. Only {@code all} can reach it.
     */
    private static final int MAX_TREND_POINTS = 240;

    private final OrganizationAnalyticsRepository analyticsRepository;

    /**
     * @param organizationName carried in rather than re-read, and out of the
     *                         cache key: it is a property of the id already in
     *                         there.
     */
    @Cacheable(
            cacheNames = CacheNames.ORGANIZATION_ANALYTICS,
            key = "#organizationId + ':' + #timeRange + ':' + #programId",
            sync = true
    )
    @Transactional(readOnly = true)
    public OrganizationAnalyticsResponse load(
            UUID organizationId,
            String organizationName,
            AnalyticsTimeRange timeRange,
            UUID programId
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = timeRange.startOf(now);

        OrganizationAnalyticsRepository.WindowSummary current =
                analyticsRepository.summarize(
                        organizationId,
                        programId,
                        start,
                        now,
                        now,
                        TRIAGE_TARGET_HOURS
                );
        OrganizationAnalyticsRepository.WindowSummary previous =
                timeRange.hasPreviousPeriod()
                        ? analyticsRepository.summarize(
                                organizationId,
                                programId,
                                timeRange.previousStartOf(now),
                                start,
                                now,
                                TRIAGE_TARGET_HOURS
                        )
                        : null;

        return new OrganizationAnalyticsResponse(
                organizationId,
                organizationName,
                timeRange.wireValue(),
                programId,
                Instant.now(),
                kpiSummary(current, previous),
                submissionTrend(
                        organizationId,
                        programId,
                        start,
                        now,
                        timeRange,
                        current.getFirstSubmittedOn()
                ),
                severityDistribution(
                        organizationId,
                        programId,
                        start,
                        now,
                        current.getTotalReports()
                ),
                topWeaknesses(
                        organizationId,
                        programId,
                        start,
                        now,
                        current.getTotalReports()
                ),
                topAssets(organizationId, programId, start, now),
                topResearchers(organizationId, programId, start, now)
        );
    }

    private KpiSummary kpiSummary(
            OrganizationAnalyticsRepository.WindowSummary current,
            OrganizationAnalyticsRepository.WindowSummary previous
    ) {
        boolean comparable = previous != null;
        long total = current.getTotalReports();

        Change totalChange = AnalyticsFigures.compare(
                total,
                comparable ? previous.getTotalReports() : 0,
                comparable
        );
        Change acceptedChange = AnalyticsFigures.compare(
                current.getAcceptedReports(),
                comparable ? previous.getAcceptedReports() : 0,
                comparable
        );
        Change rejectedChange = AnalyticsFigures.compare(
                current.getRejectedReports(),
                comparable ? previous.getRejectedReports() : 0,
                comparable
        );
        Change bountyChange = AnalyticsFigures.compare(
                current.getBountiesPaid(),
                comparable ? previous.getBountiesPaid() : BigDecimal.ZERO,
                comparable
        );
        Change researcherChange = AnalyticsFigures.compare(
                current.getActiveResearchers(),
                comparable ? previous.getActiveResearchers() : 0,
                comparable
        );

        return new KpiSummary(
                new CountMetric(
                        total,
                        totalChange.percentage(),
                        totalChange.trend()
                ),
                new AcceptedMetric(
                        current.getAcceptedReports(),
                        AnalyticsFigures.percentage(
                                current.getAcceptedReports(),
                                total
                        ),
                        acceptedChange.percentage(),
                        acceptedChange.trend()
                ),
                new RejectedMetric(
                        current.getRejectedReports(),
                        AnalyticsFigures.percentage(
                                current.getRejectedReports(),
                                total
                        ),
                        rejectedChange.percentage(),
                        rejectedChange.trend()
                ),
                new BountyMetric(
                        AnalyticsFigures.money(current.getBountiesPaid()),
                        BOUNTY_CURRENCY,
                        bountyChange.percentage(),
                        bountyChange.trend()
                ),
                current.getReputationPoints(),
                new CountMetric(
                        current.getActiveResearchers(),
                        researcherChange.percentage(),
                        researcherChange.trend()
                ),
                new SlaMetrics(
                        AnalyticsFigures.rate(current.getMeanTriageHours()),
                        AnalyticsFigures.rate(current.getMeanResolveDays()),
                        AnalyticsFigures.percentage(
                                current.getSlaMetReports(),
                                current.getSlaDecidedReports()
                        ),
                        TRIAGE_TARGET_HOURS
                )
        );
    }

    /**
     * Every bucket in the window, in order, including the ones nothing was
     * submitted in.
     *
     * @param firstSubmittedOn where an {@code all} series starts. Null means
     *                         the window is empty, and has no series to draw.
     */
    private List<SubmissionTrendPoint> submissionTrend(
            UUID organizationId,
            UUID programId,
            LocalDateTime start,
            LocalDateTime end,
            AnalyticsTimeRange timeRange,
            String firstSubmittedOn
    ) {
        AnalyticsBucket bucket = timeRange.bucket();
        Map<String, OrganizationAnalyticsRepository.TrendBucket> byStart =
                new HashMap<>();
        for (OrganizationAnalyticsRepository.TrendBucket row
                : analyticsRepository.findSubmissionTrend(
                        organizationId,
                        programId,
                        start,
                        end,
                        bucket.dateTruncUnit()
                )) {
            byStart.put(row.getBucketStart(), row);
        }

        LocalDate seriesStart = seriesStart(timeRange, start, firstSubmittedOn);
        if (seriesStart == null) {
            return List.of();
        }
        LocalDate seriesEnd = bucket.floor(end.toLocalDate());

        List<LocalDate> starts = new ArrayList<>();
        for (LocalDate at = seriesStart;
             !at.isAfter(seriesEnd);
             at = bucket.next(at)) {
            starts.add(at);
        }
        if (starts.size() > MAX_TREND_POINTS) {
            starts = starts.subList(
                    starts.size() - MAX_TREND_POINTS,
                    starts.size()
            );
        }

        List<SubmissionTrendPoint> points = new ArrayList<>(starts.size());
        for (LocalDate at : starts) {
            OrganizationAnalyticsRepository.TrendBucket row =
                    byStart.get(at.toString());
            points.add(new SubmissionTrendPoint(
                    bucket.periodKey(at),
                    bucket.label(at),
                    row == null ? 0L : row.getSubmitted(),
                    row == null ? 0L : row.getAccepted(),
                    row == null ? 0L : row.getResolved(),
                    row == null ? 0L : row.getRejected(),
                    AnalyticsFigures.money(
                            row == null ? BigDecimal.ZERO : row.getBountyPaid()
                    )
            ));
        }
        return points;
    }

    /**
     * Where the series begins: the window's own start for a bounded range, and
     * the first report there has ever been for {@code all}. Null when there is
     * nothing to draw.
     */
    private LocalDate seriesStart(
            AnalyticsTimeRange timeRange,
            LocalDateTime windowStart,
            String firstSubmittedOn
    ) {
        AnalyticsBucket bucket = timeRange.bucket();
        if (timeRange.hasPreviousPeriod()) {
            return bucket.floor(windowStart.toLocalDate());
        }
        if (firstSubmittedOn == null) {
            return null;
        }
        return bucket.floor(LocalDate.parse(firstSubmittedOn));
    }

    private SeverityDistribution severityDistribution(
            UUID organizationId,
            UUID programId,
            LocalDateTime start,
            LocalDateTime end,
            long totalReports
    ) {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        Map<Severity, BigDecimal> averages = new EnumMap<>(Severity.class);
        for (OrganizationAnalyticsRepository.SeverityTally tally
                : analyticsRepository.findSeverityDistribution(
                        organizationId,
                        programId,
                        start,
                        end
                )) {
            // Folded into NONE rather than dropped: the bands have to add up
            // to the total the tiles show.
            Severity severity = severityOrNone(tally.getSeverity());
            counts.merge(severity, tally.getTotal(), Long::sum);
            averages.putIfAbsent(severity, tally.getAverageBounty());
        }

        return new SeverityDistribution(
                band(counts, averages, Severity.CRITICAL, totalReports),
                band(counts, averages, Severity.HIGH, totalReports),
                band(counts, averages, Severity.MEDIUM, totalReports),
                band(counts, averages, Severity.LOW, totalReports),
                band(counts, averages, Severity.NONE, totalReports)
        );
    }

    private SeverityBand band(
            Map<Severity, Long> counts,
            Map<Severity, BigDecimal> averages,
            Severity severity,
            long totalReports
    ) {
        long count = counts.getOrDefault(severity, 0L);
        return new SeverityBand(
                count,
                AnalyticsFigures.percentage(count, totalReports),
                AnalyticsFigures.money(averages.get(severity))
        );
    }

    private List<VulnerabilityCategory> topWeaknesses(
            UUID organizationId,
            UUID programId,
            LocalDateTime start,
            LocalDateTime end,
            long totalReports
    ) {
        return analyticsRepository.findTopWeaknesses(
                        organizationId,
                        programId,
                        start,
                        end,
                        AnalyticsQueries.TOP_LIST_SIZE
                )
                .stream()
                .map(tally -> new VulnerabilityCategory(
                        tally.getCweId(),
                        tally.getName(),
                        tally.getTotal(),
                        AnalyticsFigures.percentage(
                                tally.getTotal(),
                                totalReports
                        ),
                        tally.getCriticalCount()
                ))
                .toList();
    }

    private List<TargetedAsset> topAssets(
            UUID organizationId,
            UUID programId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return analyticsRepository.findTopAssets(
                        organizationId,
                        programId,
                        start,
                        end,
                        AnalyticsQueries.TOP_LIST_SIZE
                )
                .stream()
                .map(tally -> new TargetedAsset(
                        tally.getAssetTarget(),
                        assetTypeOrOther(tally.getAssetType()),
                        tally.getTotalReports(),
                        tally.getCriticalCount(),
                        tally.getHighCount(),
                        AnalyticsFigures.money(tally.getTotalBounty())
                ))
                .toList();
    }

    private List<ResearcherStanding> topResearchers(
            UUID organizationId,
            UUID programId,
            LocalDateTime start,
            LocalDateTime end
    ) {
        List<OrganizationAnalyticsRepository.ResearcherTally> tallies =
                analyticsRepository.findTopResearchers(
                        organizationId,
                        programId,
                        start,
                        end,
                        AnalyticsQueries.TOP_LIST_SIZE
                );

        List<ResearcherStanding> standings = new ArrayList<>(tallies.size());
        for (int index = 0; index < tallies.size(); index++) {
            OrganizationAnalyticsRepository.ResearcherTally tally =
                    tallies.get(index);
            standings.add(new ResearcherStanding(
                    tally.getUserId(),
                    tally.getUsername(),
                    tally.getDisplayName(),
                    tally.getAvatarUrl(),
                    index + 1,
                    tally.getValidReports(),
                    tally.getCriticalReports(),
                    AnalyticsFigures.money(tally.getTotalBountiesEarned()),
                    tally.getReputationEarned()
            ));
        }
        return standings;
    }

    private Severity severityOrNone(String databaseValue) {
        if (databaseValue == null) {
            return Severity.NONE;
        }
        try {
            return Severity.valueOf(databaseValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Severity.NONE;
        }
    }

    /**
     * A value the database has and this build does not is a schema that ran
     * ahead of the application: shown as OTHER rather than failing the page
     * over a label.
     */
    private AssetType assetTypeOrOther(String databaseValue) {
        if (databaseValue == null) {
            return AssetType.OTHER;
        }
        try {
            return AssetType.valueOf(databaseValue.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AssetType.OTHER;
        }
    }
}
