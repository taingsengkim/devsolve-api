package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bucket boundaries have to be the ones {@code date_trunc} produced, or
 * the zero-filled series and the query results land a day apart and every
 * bucket reads empty.
 */
class AnalyticsBucketTest {

    @Test
    void aMonthBucketStartsOnTheFirst() {
        assertEquals(
                LocalDate.of(2026, 9, 1),
                AnalyticsBucket.MONTH.floor(LocalDate.of(2026, 9, 17))
        );
    }

    /**
     * Postgres truncates weeks to Monday. Java's own week fields are
     * locale-dependent and would start them on Sunday under a US default.
     */
    @Test
    void aWeekBucketStartsOnMondayWhateverTheServerLocale() {
        assertEquals(
                LocalDate.of(2026, 8, 31),
                AnalyticsBucket.WEEK.floor(LocalDate.of(2026, 9, 6))
        );
        assertEquals(
                LocalDate.of(2026, 8, 31),
                AnalyticsBucket.WEEK.floor(LocalDate.of(2026, 8, 31))
        );
    }

    @Test
    void aDayBucketIsTheDayItself() {
        LocalDate day = LocalDate.of(2026, 9, 3);

        assertEquals(day, AnalyticsBucket.DAY.floor(day));
    }

    @Test
    void walkingForwardCrossesAYearBoundaryCleanly() {
        assertEquals(
                LocalDate.of(2027, 1, 1),
                AnalyticsBucket.MONTH.next(LocalDate.of(2026, 12, 1))
        );
        assertEquals(
                LocalDate.of(2027, 1, 4),
                AnalyticsBucket.WEEK.next(LocalDate.of(2026, 12, 28))
        );
        assertEquals(
                LocalDate.of(2027, 1, 1),
                AnalyticsBucket.DAY.next(LocalDate.of(2026, 12, 31))
        );
    }

    @Test
    void periodKeysSortInChronologicalOrder() {
        assertEquals(
                "2026-03",
                AnalyticsBucket.MONTH.periodKey(LocalDate.of(2026, 3, 1))
        );
        assertEquals(
                "2026-09-03",
                AnalyticsBucket.DAY.periodKey(LocalDate.of(2026, 9, 3))
        );
    }

    /**
     * ISO week numbering, which is what {@code date_trunc('week', …)} agrees
     * with — the 4th of January 2027 is in week 1 of week-based year 2027,
     * not week 53 of 2026.
     */
    @Test
    void weekKeysUseIsoWeekNumbering() {
        assertEquals(
                "2027-W01",
                AnalyticsBucket.WEEK.periodKey(LocalDate.of(2027, 1, 4))
        );
    }

    @Test
    void labelsAreEnglishWhateverTheServerLocale() {
        assertEquals(
                "Mar 2026",
                AnalyticsBucket.MONTH.label(LocalDate.of(2026, 3, 1))
        );
        assertEquals(
                "3 Sep",
                AnalyticsBucket.DAY.label(LocalDate.of(2026, 9, 3))
        );
        assertEquals(
                "Week of 31 Aug",
                AnalyticsBucket.WEEK.label(LocalDate.of(2026, 8, 31))
        );
    }
}
