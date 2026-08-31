package kh.edu.istad.ite.devsoleapi.feature.reputation.dto;

import java.util.UUID;

/**
 * One ranked researcher.
 *
 * <p>On a windowed leaderboard {@code reputation}, {@code recognitionCount}
 * and {@code criticalReports} are what was earned inside the window, and
 * {@code totalReports} and {@code validReports} are null: those two are only
 * kept as lifetime running totals, and printing a lifetime number in a row
 * that claims to be about this week is worse than printing nothing.
 *
 * @param username the handle a profile URL is built from
 */
public record LeaderboardResponse(
        Integer rank,

        UUID id,

        String username,

        String fullName,

        String avatarUrl,

        String country,

        Integer reputation,

        Integer totalReports,

        Integer validReports,

        Integer criticalReports,

        Integer recognitionCount
) {
}
