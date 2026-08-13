package kh.edu.istad.ite.devsoleapi.feature.comments;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How fast one account is allowed to post comments.
 *
 * <p>The burst window is held in memory, so it is per instance: run two and a
 * determined script gets twice the burst. That is deliberate rather than
 * overlooked — there is no shared cache in this deployment, and the hourly cap
 * that actually bounds the damage is counted from the comments table, which
 * every instance and every restart agrees on. The in-memory half exists to
 * stop the fast, accidental case — a stuck retry, a double-clicked button —
 * without a database round trip on the hot path.
 *
 * <p>Both limits are per author rather than per target: moving to a different
 * showcase is not a reason to be allowed to flood.
 */
@Component
public class CommentRateLimiter {

    static final Duration BURST_WINDOW = Duration.ofMinutes(1);
    static final int BURST_LIMIT = 5;

    static final Duration SUSTAINED_WINDOW = Duration.ofHours(1);
    static final int SUSTAINED_LIMIT = 60;

    /**
     * Authors tracked before a sweep runs. Each entry is a handful of
     * timestamps, so this is a bound on memory rather than a meaningful
     * ceiling on concurrent users.
     */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<UUID, Deque<Instant>> recentPosts =
            new ConcurrentHashMap<>();

    /**
     * Records an attempt and rejects it if the author is over the burst limit.
     * The sustained limit is checked by the caller, which can count rows.
     *
     * <p>Call this last. It has no counterpart that gives a slot back, so
     * anything that can reject a comment has to have rejected it already.
     */
    void checkBurst(UUID authorId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(BURST_WINDOW);

        if (recentPosts.size() > SWEEP_THRESHOLD) {
            sweep(cutoff);
        }

        recentPosts.compute(authorId, (id, timestamps) -> {
            Deque<Instant> window = timestamps == null
                    ? new ArrayDeque<>()
                    : timestamps;
            // The deque is only ever touched inside compute, which
            // ConcurrentHashMap runs under the bin lock, so an unsynchronized
            // ArrayDeque is safe here and nowhere else.
            synchronized (window) {
                while (!window.isEmpty()
                        && window.peekFirst().isBefore(cutoff)) {
                    window.pollFirst();
                }
                if (window.size() >= BURST_LIMIT) {
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "You are commenting too quickly. Wait a moment "
                                    + "and try again."
                    );
                }
                window.addLast(now);
            }
            return window;
        });
    }

    void checkSustained(long postedInLastHour) {
        if (postedInLastHour >= SUSTAINED_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the hourly comment limit. Try again "
                            + "later."
            );
        }
    }

    private void sweep(Instant cutoff) {
        recentPosts.entrySet().removeIf(entry -> {
            Deque<Instant> window = entry.getValue();
            synchronized (window) {
                while (!window.isEmpty()
                        && window.peekFirst().isBefore(cutoff)) {
                    window.pollFirst();
                }
                return window.isEmpty();
            }
        });
    }
}
