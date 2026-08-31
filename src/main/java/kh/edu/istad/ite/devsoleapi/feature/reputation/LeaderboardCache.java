package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRepository;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The leaderboard query, behind a cache. A separate bean because
 * {@code @Cacheable} is proxy-applied: annotating a method that
 * {@link LeaderboardServiceImpl} calls on itself would cache nothing, silently.
 */
@Component
@RequiredArgsConstructor
public class LeaderboardCache {

    /**
     * Page and size come from the query string, so {@code ?page=999999} is a
     * free cache key for anyone who asks. Past this the query still runs,
     * uncached. Repeated in the {@code condition} below, which SpEL cannot read
     * this field from; {@code LeaderboardCacheTest} pins the two together.
     */
    public static final int CACHED_PAGES = 10;

    private final UserProfileRepository userProfileRepository;
    private final HacktivityRepository hacktivityRepository;
    private final LeaderboardMapper leaderboardMapper;

    /**
     * Rank is positional, not competition rank: ties get consecutive numbers,
     * decided by the id tiebreaker. That keeps rank derivable from the page
     * offset instead of costing a count query per row.
     *
     * <p>{@code period} is part of the key: without it "this week" and
     * "all time" are the same cache entry, and whichever was asked for first
     * is what both answer with.
     */
    @Cacheable(
            cacheNames = CacheNames.LEADERBOARD,
            key = "#period + ':' + #page + ':' + #size",
            condition = "#page < 10"
    )
    @Transactional(readOnly = true)
    public LeaderboardSlice load(
            LeaderboardPeriod period,
            int page,
            int size
    ) {

        return period.isWindowed()
                ? loadWindow(period, page, size)
                : loadAllTime(page, size);
    }

    private LeaderboardSlice loadAllTime(int page, int size) {

        AtomicInteger rank = new AtomicInteger(page * size + 1);

        Page<LeaderboardResponse> ranked = userProfileRepository
                .findAllByStatusOrderByReputationDescIdAsc(
                        UserStatus.ACTIVE,
                        PageRequest.of(page, size)
                )
                .map(user -> leaderboardMapper.toResponse(
                        user,
                        rank.getAndIncrement()
                ));

        return new LeaderboardSlice(
                List.copyOf(ranked.getContent()),
                ranked.getTotalElements()
        );
    }

    /**
     * A window is ranked and paged in memory rather than by the database.
     *
     * <p>The ordering it needs — points, which only {@link ReputationPolicy}
     * knows how to compute from a severity — is not a column, so there is
     * nothing for SQL to sort on without a second copy of the curve written as
     * a CASE expression. What is read instead is one grouped row per
     * researcher and severity inside the window, which is bounded by how many
     * people were recognised that week, not by the size of the feed.
     */
    private LeaderboardSlice loadWindow(
            LeaderboardPeriod period,
            int page,
            int size
    ) {

        Map<UUID, WindowedStanding> standings = new LinkedHashMap<>();

        hacktivityRepository
                .tallyRecognitionsSince(period.since(LocalDateTime.now()))
                .forEach(tally -> standings
                        .computeIfAbsent(
                                tally.getUserId(),
                                id -> new WindowedStanding()
                        )
                        .add(tally.getSeverity(), tally.getRecognitions()));

        if (standings.isEmpty()) {
            return new LeaderboardSlice(List.of(), 0);
        }

        // Suspended and removed accounts do not rank, the same rule the
        // all-time board applies — and applied before paging, so their absence
        // does not leave holes in the ranks.
        Map<UUID, UserProfile> profiles = userProfileRepository
                .findAllById(standings.keySet())
                .stream()
                .filter(profile -> profile.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        Function.identity()
                ));

        List<UUID> ranked = standings.entrySet().stream()
                .filter(entry -> profiles.containsKey(entry.getKey()))
                .sorted(Comparator
                        .comparingInt(
                                (Map.Entry<UUID, WindowedStanding> entry) ->
                                        entry.getValue().points()
                        )
                        .reversed()
                        // Same reason the all-time query breaks ties on id:
                        // equal scores in an undefined order page unstably,
                        // duplicating some researchers and skipping others.
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        int from = Math.min(page * size, ranked.size());
        int to = Math.min(from + size, ranked.size());

        AtomicInteger rank = new AtomicInteger(from + 1);

        List<LeaderboardResponse> content = ranked.subList(from, to).stream()
                .map(id -> leaderboardMapper.toWindowedResponse(
                        profiles.get(id),
                        rank.getAndIncrement(),
                        standings.get(id)
                ))
                .toList();

        return new LeaderboardSlice(content, ranked.size());
    }
}
