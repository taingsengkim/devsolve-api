package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final LeaderboardMapper leaderboardMapper;

    /**
     * Rank is positional, not competition rank: ties get consecutive numbers,
     * decided by the id tiebreaker in the query. That keeps rank derivable from
     * the page offset instead of costing a count query per row.
     */
    @Cacheable(
            cacheNames = CacheNames.LEADERBOARD,
            key = "#page + ':' + #size",
            condition = "#page < 10"
    )
    @Transactional(readOnly = true)
    public LeaderboardSlice load(int page, int size) {

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
}
