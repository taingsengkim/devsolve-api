package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsTimeRangeTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 9, 3, 14, 30);

    @Test
    void anAbsentRangeIsSixMonths() {
        assertEquals(
                AnalyticsTimeRange.LAST_6_MONTHS,
                AnalyticsTimeRange.fromWireValue(null)
        );
        assertEquals(
                AnalyticsTimeRange.LAST_6_MONTHS,
                AnalyticsTimeRange.fromWireValue("  ")
        );
    }

    @Test
    void theWireValuesAreTheOnesTheDashboardSends() {
        assertEquals(
                AnalyticsTimeRange.LAST_30_DAYS,
                AnalyticsTimeRange.fromWireValue("30d")
        );
        assertEquals(
                AnalyticsTimeRange.LAST_90_DAYS,
                AnalyticsTimeRange.fromWireValue("90d")
        );
        assertEquals(
                AnalyticsTimeRange.LAST_YEAR,
                AnalyticsTimeRange.fromWireValue("1y")
        );
        assertEquals(
                AnalyticsTimeRange.ALL_TIME,
                AnalyticsTimeRange.fromWireValue(" ALL ")
        );
    }

    /**
     * Refused rather than silently defaulted. A client sending {@code 7d}
     * would otherwise be shown six months of data labelled as a week.
     */
    @Test
    void anUnknownRangeIsRefusedWithTheAcceptedValues() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> AnalyticsTimeRange.fromWireValue("7d")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("30d"));
        assertTrue(exception.getReason().contains("all"));
    }

    @Test
    void aBoundedWindowStartsItsOwnLengthBeforeNow() {
        assertEquals(
                LocalDateTime.of(2026, 3, 3, 14, 30),
                AnalyticsTimeRange.LAST_6_MONTHS.startOf(NOW)
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 4, 14, 30),
                AnalyticsTimeRange.LAST_30_DAYS.startOf(NOW)
        );
    }

    /**
     * The comparison window sits immediately behind the current one and is the
     * same length, so a 30-day tile compares against the 30 days before it.
     */
    @Test
    void thePreviousWindowIsTheSameLengthImmediatelyBefore() {
        assertEquals(
                LocalDateTime.of(2025, 9, 3, 14, 30),
                AnalyticsTimeRange.LAST_6_MONTHS.previousStartOf(NOW)
        );
        assertEquals(
                LocalDateTime.of(2026, 7, 5, 14, 30),
                AnalyticsTimeRange.LAST_30_DAYS.previousStartOf(NOW)
        );
    }

    /**
     * All of history has nothing behind it, and every figure on that view says
     * so by carrying no change.
     */
    @Test
    void allTimeHasNoPrecedingWindowAndStartsBeforeAnyReport() {
        assertFalse(AnalyticsTimeRange.ALL_TIME.hasPreviousPeriod());
        assertTrue(
                AnalyticsTimeRange.ALL_TIME.startOf(NOW)
                        .isBefore(LocalDateTime.of(2000, 1, 1, 0, 0))
        );
    }

    @Test
    void everyBoundedRangeHasAPrecedingWindow() {
        assertTrue(AnalyticsTimeRange.LAST_30_DAYS.hasPreviousPeriod());
        assertTrue(AnalyticsTimeRange.LAST_90_DAYS.hasPreviousPeriod());
        assertTrue(AnalyticsTimeRange.LAST_6_MONTHS.hasPreviousPeriod());
        assertTrue(AnalyticsTimeRange.LAST_YEAR.hasPreviousPeriod());
    }

    /**
     * A month of daily points is a chart; a year of them is a smear. These are
     * the pairings the series length depends on.
     */
    @Test
    void theBucketWidensWithTheWindow() {
        assertEquals(
                AnalyticsBucket.DAY,
                AnalyticsTimeRange.LAST_30_DAYS.bucket()
        );
        assertEquals(
                AnalyticsBucket.WEEK,
                AnalyticsTimeRange.LAST_90_DAYS.bucket()
        );
        assertEquals(
                AnalyticsBucket.MONTH,
                AnalyticsTimeRange.LAST_6_MONTHS.bucket()
        );
        assertEquals(
                AnalyticsBucket.MONTH,
                AnalyticsTimeRange.ALL_TIME.bucket()
        );
    }
}
