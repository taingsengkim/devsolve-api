package kh.edu.istad.ite.devsoleapi.feature.platformstats;

import kh.edu.istad.ite.devsoleapi.feature.platformstats.dto.PlatformStatsResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning monthly deltas into the twelve running totals a sparkline needs.
 *
 * <p>The queries themselves are covered against a real Postgres in
 * {@code PlatformStatsRepositoryPostgresTest}. What is left here is the
 * arithmetic, and the two ways it can be quietly wrong: dropping the history
 * older than the window, so every chart appears to climb out of the origin;
 * and letting a headline disagree with the last point of its own chart.
 */
class PlatformStatsServiceTest {

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyyy-MM");

    @Test
    void anEmptyPlatformReportsZerosAndNoSeriesAtAll() {
        PlatformStatsResponse stats = serviceOver(new StubRepository()).load();

        assertEquals(0, stats.totalDisbursedUsd().signum());
        assertEquals(0L, stats.activeResearchers());
        assertEquals(0L, stats.livePrograms());
        assertEquals(0L, stats.validatedReports());
        // Null, not twelve zeros: the frontend hides the charts on this.
        assertNull(stats.historicalSeries());
        assertEquals(PlatformStatsResponse.SERIES_PERIOD, stats.seriesPeriod());
        assertEquals(PlatformStatsResponse.SERIES_MONTHS, stats.seriesMonths());
    }

    @Test
    void everySeriesIsTwelveRunningTotalsEndingAtItsOwnHeadline() {
        StubRepository repository = new StubRepository();
        repository.disbursed.put(monthsAgo(2), new BigDecimal("1000.00"));
        repository.disbursed.put(monthsAgo(0), new BigDecimal("250.00"));
        repository.researchers.put(monthsAgo(5), 3L);
        repository.researchers.put(monthsAgo(1), 4L);
        repository.livePrograms.put(monthsAgo(11), 2L);
        repository.validatedReports.put(monthsAgo(3), 40L);

        PlatformStatsResponse stats = serviceOver(repository).load();

        assertNotNull(stats.historicalSeries());
        assertEquals(
                0,
                new BigDecimal("1250.00").compareTo(stats.totalDisbursedUsd())
        );
        assertEquals(7L, stats.activeResearchers());
        assertEquals(2L, stats.livePrograms());
        assertEquals(40L, stats.validatedReports());

        // A counter standing above a chart that ends somewhere else is the one
        // failure a reader would actually notice.
        assertEquals(
                0,
                stats.totalDisbursedUsd().compareTo(
                        last(stats.historicalSeries().disbursedUsd())
                )
        );
        assertEquals(
                stats.activeResearchers(),
                last(stats.historicalSeries().researchers())
        );
        assertEquals(
                stats.livePrograms(),
                last(stats.historicalSeries().livePrograms())
        );
        assertEquals(
                stats.validatedReports(),
                last(stats.historicalSeries().validatedReports())
        );

        // Nine months of nothing, then the first payout, which then holds
        // through a month with no payouts at all.
        assertEquals(
                List.of(
                        0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                        1_000L, 1_000L, 1_250L
                ),
                stats.historicalSeries().disbursedUsd().stream()
                        .map(BigDecimal::longValueExact)
                        .toList()
        );
    }

    /**
     * A platform with years behind it. The first point of the chart has to
     * carry all of that, or every counter appears to have started from nothing
     * twelve months ago.
     */
    @Test
    void historyOlderThanTheWindowIsTheHeightTheChartStartsAt() {
        StubRepository repository = new StubRepository();
        repository.validatedReports.put(monthsAgo(30), 14_000L);
        repository.validatedReports.put(monthsAgo(6), 200L);

        PlatformStatsResponse stats = serviceOver(repository).load();

        List<Long> series = stats.historicalSeries().validatedReports();
        assertEquals(PlatformStatsResponse.SERIES_MONTHS, series.size());
        assertEquals(14_000L, series.getFirst());
        assertEquals(14_200L, last(series));
        assertEquals(14_200L, stats.validatedReports());
    }

    /** A running total can never fall — that is what makes it a running total. */
    @Test
    void everySeriesIsNonDecreasing() {
        StubRepository repository = new StubRepository();
        for (int months = 0; months < 14; months++) {
            repository.livePrograms.put(monthsAgo(months), months + 1L);
        }

        List<Long> series = serviceOver(repository)
                .load()
                .historicalSeries()
                .livePrograms();

        for (int index = 1; index < series.size(); index++) {
            assertTrue(
                    series.get(index) >= series.get(index - 1),
                    "the series falls at point " + index
            );
        }
    }

    /**
     * A row dated past the current month belongs in the headline even though no
     * point of the chart reaches it: it means a clock somewhere disagrees, and
     * dropping it would understate the total.
     */
    @Test
    void aFutureDatedRowStillCountsTowardsTheHeadline() {
        StubRepository repository = new StubRepository();
        repository.researchers.put(monthsAgo(1), 5L);
        repository.researchers.put(monthsAgo(-2), 1L);

        PlatformStatsResponse stats = serviceOver(repository).load();

        assertEquals(6L, stats.activeResearchers());
        assertEquals(5L, last(stats.historicalSeries().researchers()));
    }

    private PlatformStatsService serviceOver(StubRepository repository) {
        return new PlatformStatsService(repository);
    }

    private static String monthsAgo(int months) {
        return LocalDate.now()
                .withDayOfMonth(1)
                .minusMonths(months)
                .format(MONTH);
    }

    private static <T> T last(List<T> values) {
        return values.getLast();
    }

    /**
     * Hand-written rather than mocked: four queries returning projections of
     * two getters each is a page of {@code when(...)} stubs, where a map per
     * metric says what the fixture is directly.
     */
    private static final class StubRepository
            implements PlatformStatsRepository {

        private final Map<String, BigDecimal> disbursed =
                new LinkedHashMap<>();
        private final Map<String, Long> researchers = new LinkedHashMap<>();
        private final Map<String, Long> livePrograms = new LinkedHashMap<>();
        private final Map<String, Long> validatedReports =
                new LinkedHashMap<>();

        @Override
        public List<MonetaryPoint> disbursedByMonth() {
            List<MonetaryPoint> points = new ArrayList<>();
            disbursed.forEach((bucket, delta) -> points.add(
                    new MonetaryPoint() {
                        @Override
                        public String getBucket() {
                            return bucket;
                        }

                        @Override
                        public BigDecimal getDelta() {
                            return delta;
                        }
                    }
            ));
            return points;
        }

        @Override
        public List<CountPoint> researchersByMonth() {
            return counts(researchers);
        }

        @Override
        public List<CountPoint> liveProgramsByMonth() {
            return counts(livePrograms);
        }

        @Override
        public List<CountPoint> validatedReportsByMonth() {
            return counts(validatedReports);
        }

        private List<CountPoint> counts(Map<String, Long> deltas) {
            List<CountPoint> points = new ArrayList<>();
            deltas.forEach((bucket, delta) -> points.add(new CountPoint() {
                @Override
                public String getBucket() {
                    return bucket;
                }

                @Override
                public long getDelta() {
                    return delta;
                }
            }));
            return points;
        }
    }
}
