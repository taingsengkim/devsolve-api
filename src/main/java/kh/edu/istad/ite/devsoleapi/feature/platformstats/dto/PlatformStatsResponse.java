package kh.edu.istad.ite.devsoleapi.feature.platformstats.dto;

import java.math.BigDecimal;

/**
 * The four counters across the top of the home page, and the twelve months of
 * history behind each.
 *
 * <p>Three of them are cumulative facts that only ever grow — money paid,
 * researchers who have taken part, findings confirmed — and are deliberately
 * not reduced when a program is deleted afterwards. {@link #livePrograms} is
 * the odd one out and has to be: it is a count of what a visitor can open right
 * now, so it falls when a program is paused or closed.
 *
 * <p>Anonymous and cached for five minutes. Nothing here is specific to a
 * viewer, which is what lets it be cached publicly at all.
 *
 * @param totalDisbursedUsd every bounty ever awarded, summed, with cents
 * @param activeResearchers researchers with a live profile who have submitted
 *                          at least one report
 * @param livePrograms      programs the public listing would return today
 * @param validatedReports  reports triage has confirmed are real
 * @param historicalSeries  the sparklines, or null when the platform has no
 *                          history at all — the signal to hide the charts
 *                          rather than draw four flat lines through zero
 * @param seriesPeriod      always {@link #SERIES_PERIOD}; named in the response
 *                          so a chart can label its axis without assuming
 * @param seriesMonths      always {@link #SERIES_MONTHS}, and the length of
 *                          every list in {@code historicalSeries}
 */
public record PlatformStatsResponse(
        BigDecimal totalDisbursedUsd,
        long activeResearchers,
        long livePrograms,
        long validatedReports,
        PlatformStatsSeries historicalSeries,
        String seriesPeriod,
        int seriesMonths
) {

    /** How the series is bucketed. One month per point. */
    public static final String SERIES_PERIOD = "MONTHLY";

    /**
     * How many points each series carries, ending with the month in progress.
     * A year reads as a year on a sparkline and survives a quiet season without
     * looking flat.
     */
    public static final int SERIES_MONTHS = 12;
}
