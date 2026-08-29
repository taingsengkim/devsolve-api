package kh.edu.istad.ite.devsoleapi.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link RateLimitStore} in this instance's heap: the whole store when Redis
 * is switched off, and the fallback {@link RedisRateLimitStore} drops to when
 * Redis stops answering.
 *
 * <p>Per instance and lost on restart, which is the limitation Redis exists to
 * remove — but a ceiling per instance still turns an unbounded loop into a
 * bounded one.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    /** Bounds memory, not concurrent traffic: each entry is a timestamp and a count. */
    private static final int SWEEP_THRESHOLD = 50_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public long recordHit(String key, Duration window) {
        Instant now = Instant.now();

        if (windows.size() > SWEEP_THRESHOLD) {
            windows.values().removeIf(existing -> existing.hasExpired(now));
        }

        // compute runs under the bin lock, so read-modify-write of one key is
        // atomic even though Window is an ordinary immutable record.
        Window updated = windows.compute(key, (ignored, existing) ->
                existing == null || existing.hasExpired(now)
                        ? new Window(now.plus(window), 1L)
                        : existing.withOneMoreHit()
        );
        return updated.hits();
    }

    private record Window(Instant expiresAt, long hits) {

        boolean hasExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }

        /** Keeps the original expiry — the window is fixed, so this never extends it. */
        Window withOneMoreHit() {
            return new Window(expiresAt, hits + 1);
        }
    }
}
