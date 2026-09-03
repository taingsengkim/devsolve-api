package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The arithmetic every tile on the dashboard shares: rounding, rates, and
 * comparing a window against the one before it.
 *
 * <p>Division by zero and growth from zero are the two cases a dashboard gets
 * wrong, and both are decided here once rather than at six call sites.
 */
public final class AnalyticsFigures {

    public static final String TREND_UP = "up";
    public static final String TREND_DOWN = "down";
    public static final String TREND_FLAT = "flat";

    /** Percentages, rates and mean times all read to one decimal place. */
    private static final int RATE_SCALE = 1;

    /** Money keeps cents, whatever the sum came back as. */
    private static final int MONEY_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private AnalyticsFigures() {
    }

    /**
     * One window against the one before it.
     *
     * @param comparable false when there is no preceding window at all, as for
     *                   an {@code all} range
     * @return a change of null and a flat trend when nothing can be compared;
     *         null with an upward trend when the previous window was empty and
     *         this one is not, because growth from zero has no percentage and
     *         reporting 100% would be inventing one
     */
    public static Change compare(long current, long previous, boolean comparable) {
        return compare(
                BigDecimal.valueOf(current),
                BigDecimal.valueOf(previous),
                comparable
        );
    }

    public static Change compare(
            BigDecimal current,
            BigDecimal previous,
            boolean comparable
    ) {
        BigDecimal now = current == null ? BigDecimal.ZERO : current;
        BigDecimal before = previous == null ? BigDecimal.ZERO : previous;

        if (!comparable) {
            return new Change(null, TREND_FLAT);
        }
        if (before.signum() == 0) {
            if (now.signum() == 0) {
                return new Change(rate(BigDecimal.ZERO), TREND_FLAT);
            }
            return new Change(
                    null,
                    now.signum() > 0 ? TREND_UP : TREND_DOWN
            );
        }

        BigDecimal change = now.subtract(before)
                .multiply(HUNDRED)
                .divide(before.abs(), RATE_SCALE, RoundingMode.HALF_UP);
        return new Change(change, trendOf(change));
    }

    /**
     * {@code part} as a percentage of {@code total}, and 0 rather than an
     * error when there is no total — an organization with no reports has a 0%
     * acceptance rate, not an undefined one.
     */
    public static BigDecimal percentage(long part, long total) {
        if (total <= 0) {
            return rate(BigDecimal.ZERO);
        }
        return BigDecimal.valueOf(part)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** A rate, a mean time, or anything else read to one decimal place. */
    public static BigDecimal rate(double value) {
        return rate(BigDecimal.valueOf(value));
    }

    public static BigDecimal rate(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** An amount of money, never null, always with cents. */
    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static String trendOf(BigDecimal change) {
        int direction = change.signum();
        if (direction > 0) {
            return TREND_UP;
        }
        return direction < 0 ? TREND_DOWN : TREND_FLAT;
    }

    /**
     * @param percentage null where a percentage would be a fiction — see
     *                   {@link #compare(long, long, boolean)}
     */
    public record Change(BigDecimal percentage, String trend) {
    }
}
