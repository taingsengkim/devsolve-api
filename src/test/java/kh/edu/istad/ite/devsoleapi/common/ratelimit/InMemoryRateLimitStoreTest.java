package kh.edu.istad.ite.devsoleapi.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimitStoreTest {

    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore();

    @Test
    void countsHitsWithinTheWindowAndKeepsKeysApart() {
        Duration window = Duration.ofMinutes(1);

        assertEquals(1, store.recordHit("a", window));
        assertEquals(2, store.recordHit("a", window));
        assertEquals(3, store.recordHit("a", window));

        assertEquals(1, store.recordHit("b", window));
    }

    @Test
    void startsAFreshCountOnceTheWindowHasPassed() throws Exception {
        Duration window = Duration.ofMillis(50);

        assertEquals(1, store.recordHit("a", window));
        assertEquals(2, store.recordHit("a", window));

        Thread.sleep(80);

        assertEquals(1, store.recordHit("a", window),
                "an expired window should reset rather than keep counting");
    }

    /** Fixed, not sliding: hits inside the window must not push its expiry out. */
    @Test
    void laterHitsDoNotExtendTheWindow() throws Exception {
        Duration window = Duration.ofMillis(100);

        store.recordHit("a", window);
        Thread.sleep(60);
        assertEquals(2, store.recordHit("a", window));
        Thread.sleep(60);

        assertEquals(1, store.recordHit("a", window),
                "the window should have expired 100ms after the first hit, "
                        + "not 100ms after the last");
    }

    /** A lost increment is a limit that does not hold. */
    @Test
    void losesNoHitsUnderConcurrency() throws Exception {
        int threads = 8;
        int hitsPerThread = 500;
        Duration window = Duration.ofMinutes(1);

        AtomicLong highest = new AtomicLong();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = IntStream.range(0, threads)
                    .mapToObj(ignored -> pool.submit(() -> {
                        for (int i = 0; i < hitsPerThread; i++) {
                            highest.accumulateAndGet(
                                    store.recordHit("shared", window),
                                    Math::max
                            );
                        }
                    }))
                    .toList();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertEquals(threads * hitsPerThread, highest.get(),
                "the highest count returned should equal the number of hits");
        assertEquals(threads * hitsPerThread + 1L,
                store.recordHit("shared", window));
    }

    @Test
    void neverReturnsLessThanOne() {
        assertTrue(store.recordHit("a", Duration.ofSeconds(1)) >= 1);
    }
}
