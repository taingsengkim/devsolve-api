package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * How fast one account is allowed to post comments.
 *
 * <p>Two limits, deliberately of different kinds. The burst window lives in
 * {@link RateLimitStore}, so with Redis every instance counts against the same
 * window; it exists to stop the fast, accidental case — a stuck retry, a
 * double-clicked button — without a database round trip on the hot path. The
 * hourly cap is counted from the comments table by the caller, because that is
 * the number that has to be exact, and it is what actually bounds the damage.
 *
 * <p>Both limits are per author rather than per target: moving to a different
 * showcase is not a reason to be allowed to flood.
 */
@Component
@RequiredArgsConstructor
public class CommentRateLimiter {

    static final Duration BURST_WINDOW = Duration.ofMinutes(1);
    static final int BURST_LIMIT = 5;

    static final Duration SUSTAINED_WINDOW = Duration.ofHours(1);
    static final int SUSTAINED_LIMIT = 60;

    private final RateLimitStore rateLimitStore;

    /**
     * Records an attempt and rejects it if the author is over the burst limit.
     * The sustained limit is checked by the caller, which can count rows.
     *
     * <p>Call this last. It has no counterpart that gives a slot back, so
     * anything that can reject a comment has to have rejected it already.
     *
     * <p>A rejected attempt is still counted, but the window is never extended
     * by it, so an author who keeps retrying is let back in as soon as it
     * expires.
     */
    void checkBurst(UUID authorId) {
        long postedInWindow = rateLimitStore.recordHit(
                "comment:burst:" + authorId,
                BURST_WINDOW
        );
        if (postedInWindow > BURST_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You are commenting too quickly. Wait a moment "
                            + "and try again."
            );
        }
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
}
