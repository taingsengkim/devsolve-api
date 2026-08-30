package kh.edu.istad.ite.devsoleapi.common.listing;

import jakarta.servlet.http.HttpServletRequest;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.web.ClientAddress;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Decides whether a view is worth counting.
 *
 * <p>View counts used to increment once per request, which was harmless while
 * they were decoration. They are a sort key now, so a shell loop over a GET
 * puts anything at the top of "most viewed". One view per viewer per window is
 * enough to keep the number meaningful without pretending to be analytics.
 *
 * <p>The window lives in {@link RateLimitStore}, so with Redis configured every
 * instance agrees and a deploy no longer hands everyone a fresh allowance.
 *
 * <p>Signed-in viewers are keyed by user id. Everyone else is keyed by address
 * and user agent, which shared networks will collapse into one viewer —
 * deliberately the safe direction to be wrong in.
 */
@Component
@RequiredArgsConstructor
public class ViewCountGuard {

    private static final Duration WINDOW = Duration.ofHours(6);

    private final RateLimitStore rateLimitStore;

    /**
     * @return true the first time this viewer looks at this thing within the
     *         window, false every time after
     */
    public boolean shouldCount(String targetType, UUID targetId) {
        String key = "view:"
                + targetType.toLowerCase(Locale.ROOT)
                + ":"
                + targetId
                + ":"
                + viewerKey();
        return rateLimitStore.recordHit(key, WINDOW) == 1L;
    }

    private String viewerKey() {
        Authentication authentication = AuthUtils.getAuth();
        if (authentication instanceof JwtAuthenticationToken jwt
                && authentication.isAuthenticated()) {
            return "user:" + jwt.getToken().getSubject();
        }

        String address = ClientAddress.current();
        if (address == null) {
            // No request bound — a scheduled job or a test. Nothing to
            // attribute the view to, so let it through rather than silently
            // dropping counts.
            return "anonymous:" + UUID.randomUUID();
        }
        HttpServletRequest request = currentRequest();
        String agent = request == null ? null : request.getHeader("User-Agent");
        // Hashed, unlike the user id above: a User-Agent is caller-controlled
        // and unbounded, and this ends up in a Redis key.
        return "anon:" + fingerprint(address + "|" + (agent == null ? "" : agent));
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
