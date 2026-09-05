package kh.edu.istad.ite.devsoleapi.feature.platformstats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Twelve months of each headline figure, oldest first, for the sparkline drawn
 * under it.
 *
 * <p>Every list holds exactly {@link PlatformStatsResponse#SERIES_MONTHS}
 * entries and the last one is the figure standing above it, so a chart and its
 * counter can never disagree. The values are running totals rather than monthly
 * activity — the counters are cumulative, and a sparkline under a cumulative
 * counter has to be cumulative too or it reads as a collapse.
 *
 * <p>Nothing here is nullable. When the platform has no history at all the
 * whole record is null instead, which is the signal to hide the charts rather
 * than draw four flat lines through zero.
 *
 * @param disbursedUsd     total paid out by the end of each month, in USD
 * @param researchers      researchers who had submitted something by then
 * @param livePrograms     programs live at the end of each month — with the
 *                         caveat that a program withdrawn since is missing from
 *                         the earlier points; see
 *                         {@code PlatformStatsQueries.LIVE_PROGRAMS_BY_MONTH}
 * @param validatedReports findings triage had confirmed by then
 */
public record PlatformStatsSeries(
        List<BigDecimal> disbursedUsd,
        List<Long> researchers,
        List<Long> livePrograms,
        List<Long> validatedReports
) {
}
