package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Drops the leaderboard once, at startup.
 *
 * <p>The cache lives in Redis, which outlives the process. {@code schema.sql}
 * runs on every boot and can rewrite the very figures the board prints — the
 * report counters were corrected there for every profile on the platform — so
 * a process that comes up against a warm Redis serves the standings from
 * before its own corrections until they expire. That is a board that is wrong
 * for exactly as long as somebody is most likely to be looking at it, right
 * after a deploy.
 *
 * <p>Cheap to be wrong about: the first read after this refills it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeaderboardCacheWarmup {

    /**
     * Optional: caching is not configured in every profile, and a startup
     * tidy-up must not be the thing that stops the application coming up
     * where there is no cache to tidy.
     */
    private final ObjectProvider<CacheManager> cacheManager;

    @EventListener(ApplicationReadyEvent.class)
    public void clearStaleLeaderboard() {
        CacheManager manager = cacheManager.getIfAvailable();

        if (manager == null) {
            return;
        }

        Cache leaderboard = manager.getCache(CacheNames.LEADERBOARD);

        if (leaderboard == null) {
            return;
        }

        try {
            leaderboard.clear();
            log.info("Cleared the leaderboard cache on startup");
        } catch (RuntimeException exception) {
            // A cache that cannot be reached is not a reason to refuse to
            // start; the entries expire on their own.
            log.warn(
                    "Could not clear the leaderboard cache on startup",
                    exception
            );
        }
    }
}
