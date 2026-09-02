package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * How often one account may spend a Claude call on a duplicate check.
 *
 * <p>Every other limiter in this application is protecting other people from
 * one account. This one is also protecting the bill: the endpoint behind it is
 * the only path in the API that costs real money per request, and a form that
 * fires it on every keystroke — the exact thing the sibling
 * {@code /problems/related} endpoint is designed for — would be an expensive
 * mistake to discover from an invoice.
 *
 * <p>Two windows for the two ways that goes wrong. The burst window catches a
 * stuck retry or a missing debounce; the hourly one catches somebody working
 * through it deliberately. Both are generous against real use: checking a
 * draft five times while writing it is normal, fifty times is not.
 *
 * <p>Only reached on the path that actually calls Claude. A deployment with the
 * integration switched off serves keyword matches and is never limited.
 */
@Component
@RequiredArgsConstructor
public class ProblemDuplicateRateLimiter {

    static final Duration BURST_WINDOW = Duration.ofMinutes(1);
    static final int BURST_LIMIT = 5;

    static final Duration SUSTAINED_WINDOW = Duration.ofHours(1);
    static final int SUSTAINED_LIMIT = 40;

    private final RateLimitStore rateLimitStore;

    /**
     * @param userId the token subject, which is identity enough here and saves
     *               a profile lookup on a path that is about to spend seconds
     *               waiting on a model
     */
    void check(String userId) {
        long inBurst = rateLimitStore.recordHit(
                "problem:duplicate-check:burst:" + userId,
                BURST_WINDOW
        );
        if (inBurst > BURST_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You are checking for duplicates too quickly. Wait a "
                            + "moment and try again."
            );
        }

        long inHour = rateLimitStore.recordHit(
                "problem:duplicate-check:hour:" + userId,
                SUSTAINED_WINDOW
        );
        if (inHour > SUSTAINED_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the hourly limit for duplicate checks. "
                            + "Try again later."
            );
        }
    }
}
