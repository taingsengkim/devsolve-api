package kh.edu.istad.ite.devsoleapi.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps a Redis outage out of the response. Spring's default handler rethrows,
 * so an unreachable cache turns a {@code @Cacheable} read into a 500 instead of
 * the database call it is supposed to be an optimisation over.
 *
 * <p>A failed evict is the one case that costs something: the stale entry stays
 * until its TTL expires. That is bounded and still better than failing the
 * write that triggered it.
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final long WARN_INTERVAL_NANOS =
            Duration.ofMinutes(1).toNanos();

    /** An outage hits every request, so warnings are throttled, not the behaviour. */
    private final AtomicLong lastWarnedAt = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        report("read", cache, key, exception);
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        report("write", cache, key, exception);
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        report("evict", cache, key, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        report("clear", cache, null, exception);
    }

    private void report(
            String operation,
            Cache cache,
            Object key,
            RuntimeException exception
    ) {
        if (shouldWarn()) {
            log.warn(
                    "Cache {} failed on '{}' (key {}); serving from the "
                            + "database until Redis answers again",
                    operation,
                    cache.getName(),
                    key,
                    exception
            );
        } else {
            log.debug(
                    "Cache {} failed on '{}' (key {})",
                    operation,
                    cache.getName(),
                    key,
                    exception
            );
        }
    }

    private boolean shouldWarn() {
        long now = System.nanoTime();
        long last = lastWarnedAt.get();
        return (last == Long.MIN_VALUE || now - last >= WARN_INTERVAL_NANOS)
                && lastWarnedAt.compareAndSet(last, now);
    }
}
