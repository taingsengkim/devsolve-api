package kh.edu.istad.ite.devsoleapi.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private StringRedisTemplate redis;

    @Test
    void returnsTheCountRedisReports() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(7L);

        RedisRateLimitStore store =
                new RedisRateLimitStore(redis, new InMemoryRateLimitStore());

        assertEquals(7L, store.recordHit("comment:burst:abc", WINDOW));
    }

    @Test
    void namespacesKeysAndPassesTheWindowInMilliseconds() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        new RedisRateLimitStore(redis, new InMemoryRateLimitStore())
                .recordHit("view:problem:abc", Duration.ofHours(6));

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<String> args = ArgumentCaptor.captor();
        verify(redis).execute(any(RedisScript.class), keys.capture(), args.capture());

        assertEquals(List.of("devsolve:rl:view:problem:abc"), keys.getValue());
        assertEquals(Long.toString(Duration.ofHours(6).toMillis()), args.getValue());
    }

    /**
     * The property the whole design rests on: Redis going down must cost
     * accuracy, never availability.
     */
    @Test
    void fallsBackToTheInMemoryStoreWhenRedisIsUnreachable() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("nope"));

        RedisRateLimitStore store =
                new RedisRateLimitStore(redis, new InMemoryRateLimitStore());

        assertEquals(1L, store.recordHit("k", WINDOW));
        assertEquals(2L, store.recordHit("k", WINDOW));
        assertEquals(3L, store.recordHit("k", WINDOW));
    }

    /** Inventing a count here would mean returning 1 forever — a limiter that never limits. */
    @Test
    void fallsBackWhenRedisAnswersWithNothing() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        RedisRateLimitStore store =
                new RedisRateLimitStore(redis, new InMemoryRateLimitStore());

        assertEquals(1L, store.recordHit("k", WINDOW));
        assertEquals(2L, store.recordHit("k", WINDOW));
    }

    /** Redis is authoritative again the moment it answers, not once the fallback catches up. */
    @Test
    void returnsToRedisAsSoonAsItAnswersAgain() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("nope"))
                .thenReturn(42L);

        RedisRateLimitStore store =
                new RedisRateLimitStore(redis, new InMemoryRateLimitStore());

        assertEquals(1L, store.recordHit("k", WINDOW));
        assertEquals(42L, store.recordHit("k", WINDOW));
    }
}
