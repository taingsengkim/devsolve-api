package kh.edu.istad.ite.devsoleapi.config.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

public final class AuthUtils {

    private AuthUtils() {
    }

    public static String extractUserId() {
        return extractJwtPrincipal().getToken().getSubject();
    }

    public static boolean hasRole(String role) {
        Authentication authentication = getAuth();
        if (!isAuthenticated(authentication)) {
            return false;
        }

        String normalizedRole = role.startsWith("ROLE_")
                ? role
                : "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority()
                        .equalsIgnoreCase(normalizedRole));
    }

    public static String extractJwt() {
        return extractJwtPrincipal().getToken().getTokenValue();
    }

    public static Authentication getAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static JwtAuthenticationToken extractJwtPrincipal() {
        Authentication authentication = getAuth();
        if (!isAuthenticated(authentication)
                || !(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "A valid Keycloak access token is required"
            );
        }
        return jwtAuthentication;
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
