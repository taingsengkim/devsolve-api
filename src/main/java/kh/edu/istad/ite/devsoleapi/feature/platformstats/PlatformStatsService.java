package kh.edu.istad.ite.devsoleapi.feature.platformstats;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.platformstats.dto.PlatformStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.platformstats.dto.PlatformStatsSeries;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The home page's four counters, computed from four aggregates and held for
 * five minutes.
 *
 * <p>Each aggregate comes back as monthly deltas over the whole history of the
 * platform — never a running total — and both the headline and its sparkline
 * are derived from that one list here. Summing the same rows for both is what
 * makes the last point of a chart equal the number printed above it; two
 * queries could not promise that.
 *
 * <p>The cache key is a constant. There are no parameters, no viewer and no
 * filters — one entry is the whole endpoint — and the TTL is what keeps it
 * moving, since nothing evicts it: every payout, submission, triage decision
 * and publish shifts one of these figures, and chasing all of those with
 * {@code @CacheEvict} would be four write paths' worth of coupling for a
 * counter nobody reads to the second.
 */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    /** Money keeps cents, whatever the sum came back as. */
    private static final int MONEY_SCALE = 2;

    private static final DateTimeFormatter MONTH_KEY =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private final PlatformStatsRepository statsRepository;

    /**
     * Called through the proxy from the controller, which is what makes the
     * {@code @Cacheable} apply — see {@code CacheConfig}. {@code sync} so an
     * expired entry costs one round of four aggregates rather than one per
     * request that arrives while it is being rebuilt.
     */
    @Cacheable(cacheNames = CacheNames.PLATFORM_STATS, key = "'all'", sync = true)
    @Transactional(readOnly = true)
    public PlatformStatsResponse load() {
        List<String> months = recentMonthKeys(LocalDate.now());

        Map<String, BigDecimal> disbursed = toDeltas(
                statsRepository.disbursedByMonth(),
                PlatformStatsRepository.MonetaryPoint::getBucket,
                point -> point.getDelta() == null
                        ? BigDecimal.ZERO
                        : point.getDelta()
        );
        Map<String, BigDecimal> researchers =
                toCountDeltas(statsRepository.researchersByMonth());
        Map<String, BigDecimal> livePrograms =
                toCountDeltas(statsRepository.liveProgramsByMonth());
        Map<String, BigDecimal> validatedReports =
                toCountDeltas(statsRepository.validatedReportsByMonth());

        boolean hasHistory = !disbursed.isEmpty()
                || !researchers.isEmpty()
                || !livePrograms.isEmpty()
                || !validatedReports.isEmpty();

        return new PlatformStatsResponse(
                money(total(disbursed)),
                total(researchers).longValueExact(),
                total(livePrograms).longValueExact(),
                total(validatedReports).longValueExact(),
                hasHistory
                        ? new PlatformStatsSeries(
                                runningTotals(disbursed, months).stream()
                                        .map(PlatformStatsService::money)
                                        .toList(),
                                counts(runningTotals(researchers, months)),
                                counts(runningTotals(livePrograms, months)),
                                counts(runningTotals(validatedReports, months))
                        )
                        : null,
                PlatformStatsResponse.SERIES_PERIOD,
                PlatformStatsResponse.SERIES_MONTHS
        );
    }

    /**
     * The last {@link PlatformStatsResponse#SERIES_MONTHS} month keys, oldest
     * first, ending with the month in progress. The current month is included
     * deliberately: a counter whose chart stopped last month would show today's
     * figure hanging above a line that never reaches it.
     */
    private List<String> recentMonthKeys(LocalDate today) {
        int months = PlatformStatsResponse.SERIES_MONTHS;
        List<String> keys = new ArrayList<>(months);
        LocalDate oldest = today.withDayOfMonth(1).minusMonths(months - 1L);
        for (int index = 0; index < months; index++) {
            keys.add(oldest.plusMonths(index).format(MONTH_KEY));
        }
        return keys;
    }

    /**
     * The running total at the end of each of {@code months}.
     *
     * <p>Starts from everything older than the first of them rather than from
     * zero, so a platform with five years behind it does not draw a chart that
     * climbs out of the origin every time.
     */
    private List<BigDecimal> runningTotals(
            Map<String, BigDecimal> deltas,
            List<String> months
    ) {
        String firstMonth = months.getFirst();
        BigDecimal running = deltas.entrySet().stream()
                .filter(entry -> entry.getKey().compareTo(firstMonth) < 0)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BigDecimal> series = new ArrayList<>(months.size());
        for (String month : months) {
            running = running.add(
                    deltas.getOrDefault(month, BigDecimal.ZERO)
            );
            series.add(running);
        }
        return series;
    }

    /**
     * Every delta, including any the series cannot reach: a row dated beyond
     * the current month is a clock disagreeing with itself somewhere, and it
     * belongs in the headline even though no point of the chart covers it.
     */
    private BigDecimal total(Map<String, BigDecimal> deltas) {
        return deltas.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> toCountDeltas(
            List<PlatformStatsRepository.CountPoint> points
    ) {
        return toDeltas(
                points,
                PlatformStatsRepository.CountPoint::getBucket,
                point -> BigDecimal.valueOf(point.getDelta())
        );
    }

    /**
     * Keyed by month, merging rather than replacing. The queries group by month
     * already, so a duplicate key means two months truncated to one string —
     * which cannot happen — but summing costs nothing and never silently drops
     * a row.
     */
    private <T> Map<String, BigDecimal> toDeltas(
            List<T> points,
            Function<T, String> bucket,
            Function<T, BigDecimal> delta
    ) {
        Map<String, BigDecimal> deltas = new HashMap<>();
        for (T point : points) {
            String key = bucket.apply(point);
            if (key == null) {
                continue;
            }
            deltas.merge(key, delta.apply(point), BigDecimal::add);
        }
        return deltas;
    }

    private List<Long> counts(List<BigDecimal> series) {
        return series.stream().map(BigDecimal::longValueExact).toList();
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
