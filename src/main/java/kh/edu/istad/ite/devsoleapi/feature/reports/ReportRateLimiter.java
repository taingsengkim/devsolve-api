package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * How fast one researcher is allowed to file reports.
 *
 * <p>Built like {@link kh.edu.istad.ite.devsoleapi.feature.comments.CommentRateLimiter}:
 * a burst window in {@link RateLimitStore}, so with Redis every instance counts
 * against the same window, and an hourly cap counted from the reports table
 * because that is the number that has to be exact.
 *
 * <p>The limits are lower than the comment ones and the windows are longer. A
 * report is a considered artifact, not a remark — nobody writes five in a
 * minute — and the cost of a flood is higher than noise: a scanner dumping
 * low-quality findings buries a real vulnerability in the triage queue, which
 * is the one thing an organization must not miss.
 *
 * <p>Per reporter rather than per program. Moving to another program is not a
 * reason to be allowed to flood, and a researcher spraying every program an
 * organization runs is the case worth stopping most.
 */
@Component
@RequiredArgsConstructor
public class ReportRateLimiter {

    static final Duration BURST_WINDOW = Duration.ofMinutes(5);
    static final int BURST_LIMIT = 5;

    static final Duration SUSTAINED_WINDOW = Duration.ofHours(1);
    static final int SUSTAINED_LIMIT = 20;

    private final RateLimitStore rateLimitStore;

    /**
     * Records an attempt and rejects it if the reporter is over the burst
     * limit.
     *
     * <p>Call this last. It has no counterpart that gives a slot back, so
     * anything that can reject a report has to have rejected it already.
     */
    void checkBurst(UUID reporterId) {
        long submittedInWindow = rateLimitStore.recordHit(
                "report:burst:" + reporterId,
                BURST_WINDOW
        );
        if (submittedInWindow > BURST_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You are submitting reports too quickly. Wait a few "
                            + "minutes and try again."
            );
        }
    }

    void checkSustained(long submittedInLastHour) {
        if (submittedInLastHour >= SUSTAINED_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "You have reached the hourly report limit. Try again "
                            + "later."
            );
        }
    }
}
