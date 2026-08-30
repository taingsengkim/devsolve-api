package kh.edu.istad.ite.devsoleapi.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link RateLimitStore} shared by every instance, in Redis.
 *
 * <p>A Redis outage degrades to {@link InMemoryRateLimitStore} rather than
 * propagating: these are soft guards, so being wrong costs a double-counted
 * view or an extra allowed comment, while failing costs a user their comment.
 * The fallback keeps counting throughout, so it is warm when it is needed, and
 * recovery is automatic because every call tries Redis first.
 */
@Slf4j
public class RedisRateLimitStore implements RateLimitStore {

    /** Namespaces counters away from cache entries, which carry their own prefix. */
    private static final String KEY_PREFIX = "devsolve:rl:";

    private static final RedisScript<Long> RECORD_HIT = recordHitScript();

    private final StringRedisTemplate redis;
    private final RateLimitStore fallback;

    /** Guards the log, not the behaviour: one line per outage, not per request. */
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public RedisRateLimitStore(
            StringRedisTemplate redis,
            RateLimitStore fallback
    ) {
        this.redis = redis;
        this.fallback = fallback;
    }

    @Override
    public long recordHit(String key, Duration window) {
        try {
            Long hits = redis.execute(
                    RECORD_HIT,
                    List.of(KEY_PREFIX + key),
                    Long.toString(window.toMillis())
            );
            if (hits == null) {
                // No exception and no answer. Nothing to trust, so treat it as
                // an outage rather than inventing a count.
                return fallback.recordHit(key, window);
            }
            if (degraded.compareAndSet(true, false)) {
                log.info("Redis is answering again; rate limits are shared "
                        + "across instances once more");
            }
            return hits;
        } catch (DataAccessException redisUnavailable) {
            if (degraded.compareAndSet(false, true)) {
                log.warn("Redis is unreachable; rate limits and view "
                                + "de-duplication fall back to per-instance "
                                + "counters until it returns",
                        redisUnavailable);
            }
            return fallback.recordHit(key, window);
        }
    }

    private static RedisScript<Long> recordHitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/rate-limit-window.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
