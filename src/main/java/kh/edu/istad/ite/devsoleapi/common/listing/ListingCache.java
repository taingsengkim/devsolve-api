package kh.edu.istad.ite.devsoleapi.common.listing;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;

/**
 * Cache headers for the public feeds.
 *
 * <p>The obvious move — {@code Cache-Control: public} on an endpoint anyone
 * can read — would be wrong here, and quietly. These listings carry
 * viewer-specific fields: whether you voted, whether you bookmarked, whether
 * you may edit. A shared cache holding one reader's copy would hand it to the
 * next reader along with their neighbour's vote state.
 *
 * <p>So: shared caching only for anonymous requests, private caching for
 * signed-in ones, and {@code Vary: Authorization} either way so nothing
 * downstream conflates the two.
 */
public final class ListingCache {

    /**
     * Short enough that a new showcase or problem shows up promptly, long
     * enough to absorb the burst of a link being shared somewhere.
     */
    private static final Duration ANONYMOUS_TTL = Duration.ofSeconds(60);

    private static final Duration AUTHENTICATED_TTL = Duration.ofSeconds(15);

    private ListingCache() {
    }

    public static <T> ResponseEntity<T> publicListing(T body) {
        boolean authenticated = AuthUtils.getAuth()
                instanceof JwtAuthenticationToken authentication
                && ((Authentication) authentication).isAuthenticated();

        CacheControl cacheControl = authenticated
                ? CacheControl.maxAge(AUTHENTICATED_TTL).cachePrivate()
                : CacheControl.maxAge(ANONYMOUS_TTL).cachePublic();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
                .body(body);
    }
}
