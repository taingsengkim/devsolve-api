package kh.edu.istad.ite.devsoleapi.feature.reputation;

import java.time.LocalDateTime;
import java.time.Period;

/**
 * The window a leaderboard ranks over.
 *
 * <p>{@link #ALL_TIME} reads the running reputation total on each profile.
 * The windowed periods cannot: that total is a single number with no history
 * behind it, so "this week" asked of it is just all-time under another label —
 * which is what a "Top Hackers This Week" widget was showing.
 *
 * <p>They are recomputed instead from the recognitions inside the window,
 * which is exactly what moved reputation in the first place, priced through
 * {@link ReputationPolicy} so there is still one place that says what a
 * severity is worth.
 */
public enum LeaderboardPeriod {

    DAY(Period.ofDays(1)),

    WEEK(Period.ofWeeks(1)),

    MONTH(Period.ofMonths(1)),

    ALL_TIME(null);

    private final Period window;

    LeaderboardPeriod(Period window) {
        this.window = window;
    }

    public boolean isWindowed() {
        return window != null;
    }

    /**
     * The cut-off this period starts at.
     *
     * @throws IllegalStateException on {@link #ALL_TIME}, which has none
     */
    public LocalDateTime since(LocalDateTime now) {

        if (window == null) {
            throw new IllegalStateException(
                    "ALL_TIME has no cut-off; check isWindowed() first"
            );
        }

        return now.minus(window);
    }
}
