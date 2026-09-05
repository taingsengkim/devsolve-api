package kh.edu.istad.ite.devsoleapi.feature.platformstats;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The four aggregates behind {@code GET /api/v1/public/stats}. The SQL lives in
 * {@link PlatformStatsQueries}.
 *
 * <p>Extends the bare {@link Repository} marker so it offers these four reads
 * and nothing else: an anonymous landing page has no business holding a handle
 * that can save or delete a report.
 */
public interface PlatformStatsRepository extends Repository<Report, UUID> {

    /** Bounties awarded per month, oldest first. */
    @Query(value = PlatformStatsQueries.DISBURSED_BY_MONTH, nativeQuery = true)
    List<MonetaryPoint> disbursedByMonth();

    /** Researchers by the month of their first submission, oldest first. */
    @Query(value = PlatformStatsQueries.RESEARCHERS_BY_MONTH, nativeQuery = true)
    List<CountPoint> researchersByMonth();

    /** Currently live programs by the month they went live, oldest first. */
    @Query(
            value = PlatformStatsQueries.LIVE_PROGRAMS_BY_MONTH,
            nativeQuery = true
    )
    List<CountPoint> liveProgramsByMonth();

    /** Confirmed findings by the month triage confirmed them, oldest first. */
    @Query(
            value = PlatformStatsQueries.VALIDATED_REPORTS_BY_MONTH,
            nativeQuery = true
    )
    List<CountPoint> validatedReportsByMonth();

    /**
     * One month's contribution to a running total. Never a cumulative figure —
     * see {@link PlatformStatsQueries}.
     */
    interface CountPoint {

        /** The month this delta belongs to, as {@code YYYY-MM}. */
        String getBucket();

        long getDelta();
    }

    interface MonetaryPoint {

        String getBucket();

        /** Coalesced in SQL, so zero rather than null for a points-only month. */
        BigDecimal getDelta();
    }
}
