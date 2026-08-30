package kh.edu.istad.ite.devsoleapi.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The point of the handler is that none of these propagate: Spring's default
 * rethrows, which turns an unreachable Redis into a 500 on every cached read.
 */
class LoggingCacheErrorHandlerTest {

    private final LoggingCacheErrorHandler handler = new LoggingCacheErrorHandler();

    private final Cache cache = new ConcurrentMapCache("categories");

    private final RedisConnectionFailureException unreachable =
            new RedisConnectionFailureException("no connection");

    @Test
    void swallowsAReadFailureSoTheCallFallsThroughToTheDatabase() {
        assertDoesNotThrow(() ->
                handler.handleCacheGetError(unreachable, cache, "all:any"));
    }

    @Test
    void swallowsAWriteFailure() {
        assertDoesNotThrow(() ->
                handler.handleCachePutError(unreachable, cache, "all:any", "value"));
    }

    @Test
    void swallowsAnEvictFailure() {
        assertDoesNotThrow(() ->
                handler.handleCacheEvictError(unreachable, cache, "all:any"));
    }

    @Test
    void swallowsAClearFailure() {
        assertDoesNotThrow(() -> handler.handleCacheClearError(unreachable, cache));
    }

    @Test
    void keepsSwallowingOncePastTheWarningThrottle() {
        for (int i = 0; i < 100; i++) {
            assertDoesNotThrow(() ->
                    handler.handleCacheGetError(unreachable, cache, "all:any"));
        }
    }
}
