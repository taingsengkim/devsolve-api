package kh.edu.istad.ite.devsoleapi.common.ratelimit;

import java.time.Duration;

/**
 * A counter that forgets: bump a key, learn what it now reads, have it expire
 * on its own.
 *
 * <p>The window is fixed, not sliding — the first hit starts the clock and
 * later hits never push it out, so a caller that keeps retrying is let back in
 * when the window ends rather than held out for as long as it keeps trying.
 *
 * <p>Implementations must never throw for infrastructure reasons; a limiter
 * that fails when its store is unreachable has turned a soft guard into an
 * outage.
 */
public interface RateLimitStore {

    /**
     * Records one hit against {@code key} and returns how many have been
     * recorded in the current window, this one included. Callers wanting "only
     * the first" test for {@code == 1}; callers wanting "at most N" test for
     * {@code > N}.
     *
     * @param key caller-namespaced; implementations add their own prefix
     */
    long recordHit(String key, Duration window);
}
