package kh.edu.istad.ite.devsoleapi.feature.reputation.dto;

import java.util.List;

/**
 * One cached page of the leaderboard. {@code PageImpl} has no no-argument
 * constructor, so it serializes but will not read back; the service rebuilds
 * the {@code Page} from this.
 */
public record LeaderboardSlice(
        List<LeaderboardResponse> content,
        long totalElements
) {
}
