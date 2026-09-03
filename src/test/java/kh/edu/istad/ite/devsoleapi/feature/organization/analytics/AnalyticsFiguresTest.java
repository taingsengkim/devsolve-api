package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.AnalyticsFigures.Change;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The two cases a dashboard gets wrong — dividing by zero, and growing from
 * it — plus the rounding every tile depends on.
 */
class AnalyticsFiguresTest {

    @Test
    void growthIsAPercentageOfThePreviousWindow() {
        Change change = AnalyticsFigures.compare(248L, 217L, true);

        assertEquals(new BigDecimal("14.3"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_UP, change.trend());
    }

    @Test
    void aFallIsNegativeAndTrendsDown() {
        Change change = AnalyticsFigures.compare(45L, 47L, true);

        assertEquals(new BigDecimal("-4.3"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_DOWN, change.trend());
    }

    @Test
    void anUnchangedWindowIsFlatAtZeroRatherThanNull() {
        Change change = AnalyticsFigures.compare(30L, 30L, true);

        assertEquals(new BigDecimal("0.0"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_FLAT, change.trend());
    }

    /**
     * The empty-organization case. Both windows are zero, so nothing moved —
     * a null here would have the dashboard render "—" on a brand new account
     * where "0%" is the honest answer.
     */
    @Test
    void zeroAgainstZeroIsFlatAtZero() {
        Change change = AnalyticsFigures.compare(0L, 0L, true);

        assertEquals(new BigDecimal("0.0"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_FLAT, change.trend());
    }

    /**
     * A first report after a quiet window. There is no percentage to report —
     * reporting 100% would be inventing one — but the direction is not in
     * doubt.
     */
    @Test
    void growthFromAnEmptyWindowHasNoPercentageButStillTrendsUp() {
        Change change = AnalyticsFigures.compare(7L, 0L, true);

        assertNull(change.percentage());
        assertEquals(AnalyticsFigures.TREND_UP, change.trend());
    }

    @Test
    void anAllTimeWindowHasNothingToCompareAgainst() {
        Change change = AnalyticsFigures.compare(248L, 0L, false);

        assertNull(change.percentage());
        assertEquals(AnalyticsFigures.TREND_FLAT, change.trend());
    }

    @Test
    void moneyComparesOnTheSameTerms() {
        Change change = AnalyticsFigures.compare(
                new BigDecimal("54250.00"),
                new BigDecimal("48437.50"),
                true
        );

        assertEquals(new BigDecimal("12.0"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_UP, change.trend());
    }

    @Test
    void aNullSumIsTreatedAsNothingPaid() {
        Change change = AnalyticsFigures.compare(null, null, true);

        assertEquals(new BigDecimal("0.0"), change.percentage());
        assertEquals(AnalyticsFigures.TREND_FLAT, change.trend());
    }

    @Test
    void aShareOfNothingIsZeroRatherThanAnError() {
        assertEquals(new BigDecimal("0.0"), AnalyticsFigures.percentage(0, 0));
    }

    @Test
    void sharesRoundToOneDecimal() {
        assertEquals(
                new BigDecimal("67.7"),
                AnalyticsFigures.percentage(168, 248)
        );
    }

    @Test
    void moneyAlwaysCarriesCentsAndNeverArrivesNull() {
        assertEquals(new BigDecimal("0.00"), AnalyticsFigures.money(null));
        assertEquals(
                new BigDecimal("1250.00"),
                AnalyticsFigures.money(new BigDecimal("1250"))
        );
    }

    @Test
    void meanTimesRoundToOneDecimal() {
        assertEquals(new BigDecimal("14.5"), AnalyticsFigures.rate(14.4666));
    }
}
