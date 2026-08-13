package kh.edu.istad.ite.devsoleapi.common.listing;

import jakarta.servlet.http.HttpServletRequest;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a view is worth counting.
 *
 * <p>View counts used to increment once per request, which was harmless while
 * they were decoration. They are a sort key now, so a shell loop over a GET
 * puts anything at the top of "most viewed". One view per viewer per window is
 * enough to keep the number meaningful without pretending to be analytics.
 *
 * <p>Held in memory, so it is per instance and resets on deploy — the same
 * trade the comment rate limiter makes, for the same reason: there is no
 * shared cache here. That ceiling still turns an unbounded loop into a handful
 * of counted views per deploy, which is the difference that matters. A shared
 * store or a views table would make it exact; neither is worth adding until
 * something depends on the number being exact.
 *
 * <p>Signed-in viewers are keyed by user id. Everyone else is keyed by address
 * and user agent, which shared networks will collapse into one viewer —
 * deliberately the safe direction to be wrong in.
 */
@Component
public class ViewCountGuard {

    private static final Duration WINDOW = Duration.ofHours(6);

    /**
     * Distinct viewers remembered before a sweep runs. Each entry is one
     * timestamp, so this bounds memory rather than concurrent traffic.
     */
    private static final int SWEEP_THRESHOLD = 50_000;

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    /**
     * @return true the first time this viewer looks at this thing within the
     *         window, false every time after
     */
    public boolean shouldCount(UUID targetId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);

        if (seen.size() > SWEEP_THRESHOLD) {
            seen.values().removeIf(at -> at.isBefore(cutoff));
        }

        String key = viewerKey() + ":" + targetId;
        Instant previous = seen.merge(
                key,
                now,
                (existing, replacement) ->
                        existing.isBefore(cutoff) ? replacement : existing
        );
        return previous.equals(now);
    }

    private String viewerKey() {
        Authentication authentication = AuthUtils.getAuth();
        if (authentication instanceof JwtAuthenticationToken jwt
                && authentication.isAuthenticated()) {
            return "user:" + jwt.getToken().getSubject();
        }

        HttpServletRequest request = currentRequest();
        if (request == null) {
            // No request bound — a scheduled job or a test. Nothing to
            // attribute the view to, so let it through rather than silently
            // dropping counts.
            return "anonymous:" + UUID.randomUUID();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String address = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        String agent = request.getHeader("User-Agent");
        return "anon:" + address + ":" + (agent == null ? "" : agent);
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
