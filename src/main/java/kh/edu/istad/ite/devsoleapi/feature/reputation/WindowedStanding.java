package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

/**
 * What one researcher earned inside a leaderboard window, accumulated as the
 * severity tallies are read.
 *
 * <p>Mutable and package-private: it exists for the length of one query fold
 * and never leaves {@link LeaderboardCache}.
 */
final class WindowedStanding {

    private int points;
    private int findings;
    private int criticals;

    void add(Severity severity, long count) {

        int times = (int) count;

        points += ReputationPolicy.pointsFor(severity) * times;
        findings += times;

        if (severity == Severity.CRITICAL) {
            criticals += times;
        }
    }

    int points() {
        return points;
    }

    /** Findings resolved inside the window — what earned the points above. */
    int findings() {
        return findings;
    }

    int criticals() {
        return criticals;
    }
}
