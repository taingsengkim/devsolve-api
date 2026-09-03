package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The aggregates behind the company analytics dashboard. The SQL lives in
 * {@link AnalyticsQueries}.
 *
 * <p>Extends the bare {@link Repository} marker so it offers these six reads
 * and nothing else — an analytics view has no business saving or deleting a
 * report.
 */
public interface OrganizationAnalyticsRepository
        extends Repository<Report, UUID> {

    /**
     * Every headline figure for one window, in one pass.
     *
     * @param asOf              when "has this missed its triage target" is
     *                          being asked. Separate from {@code endDate} so
     *                          the preceding window is judged against the
     *                          clock now, not against its own trailing edge.
     * @param triageTargetHours the triage SLA the compliance figure is
     *                          measured against
     */
    @Query(value = AnalyticsQueries.SUMMARY, nativeQuery = true)
    WindowSummary summarize(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("asOf") LocalDateTime asOf,
            @Param("triageTargetHours") int triageTargetHours
    );

    /**
     * @param bucket a {@code date_trunc} unit, only ever supplied by
     *               {@link AnalyticsBucket#dateTruncUnit()}
     */
    @Query(value = AnalyticsQueries.SUBMISSION_TREND, nativeQuery = true)
    List<TrendBucket> findSubmissionTrend(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("bucket") String bucket
    );

    @Query(value = AnalyticsQueries.SEVERITY_DISTRIBUTION, nativeQuery = true)
    List<SeverityTally> findSeverityDistribution(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = AnalyticsQueries.TOP_WEAKNESSES, nativeQuery = true)
    List<WeaknessTally> findTopWeaknesses(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("maxResults") int maxResults
    );

    @Query(value = AnalyticsQueries.TOP_ASSETS, nativeQuery = true)
    List<AssetTally> findTopAssets(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("maxResults") int maxResults
    );

    @Query(value = AnalyticsQueries.TOP_RESEARCHERS, nativeQuery = true)
    List<ResearcherTally> findTopResearchers(
            @Param("organizationId") UUID organizationId,
            @Param("programId") UUID programId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("maxResults") int maxResults
    );

    /**
     * Never null: the aggregates carry no {@code GROUP BY}, so an
     * organization with no reports at all still gets one row of zeros.
     */
    interface WindowSummary {

        long getTotalReports();

        long getAcceptedReports();

        long getRejectedReports();

        long getResolvedReports();

        long getActiveResearchers();

        BigDecimal getBountiesPaid();

        long getReputationPoints();

        double getMeanTriageHours();

        double getMeanResolveDays();

        /**
         * Reports whose triage target has been settled one way or the other —
         * triaged, or still waiting with the deadline already past. The
         * denominator of the compliance figure.
         */
        long getSlaDecidedReports();

        long getSlaMetReports();

        /**
         * The earliest submission in the window as {@code YYYY-MM-DD}, or null
         * when it holds none. Only {@code timeRange=all} needs it, to know
         * where to start drawing the trend.
         */
        String getFirstSubmittedOn();
    }

    interface TrendBucket {

        /** The bucket's first day, as {@code YYYY-MM-DD}. */
        String getBucketStart();

        long getSubmitted();

        long getAccepted();

        long getResolved();

        long getRejected();

        BigDecimal getBountyPaid();
    }

    interface SeverityTally {

        /**
         * The lower-case database value, or null for a report carrying no
         * severity at all.
         */
        String getSeverity();

        long getTotal();

        BigDecimal getAverageBounty();
    }

    interface WeaknessTally {

        String getCweId();

        String getName();

        long getTotal();

        long getCriticalCount();
    }

    interface AssetTally {

        String getAssetTarget();

        /** The lower-case database value of {@code asset_type_enum}. */
        String getAssetType();

        long getTotalReports();

        long getCriticalCount();

        long getHighCount();

        BigDecimal getTotalBounty();
    }

    interface ResearcherTally {

        UUID getUserId();

        String getUsername();

        String getDisplayName();

        String getAvatarUrl();

        long getValidReports();

        long getCriticalReports();

        BigDecimal getTotalBountiesEarned();

        long getReputationEarned();
    }
}
