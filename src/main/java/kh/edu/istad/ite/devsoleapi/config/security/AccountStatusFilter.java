package kh.edu.istad.ite.devsoleapi.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.AccountStatusService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a moderation status into an actual block.
 *
 * <p>Without this, suspending or banning a user only hid them from public
 * listings: their Keycloak account stayed enabled, so they kept receiving valid
 * tokens and could carry on posting. Disabling the account in Keycloak alone is
 * not enough either, because tokens already issued stay valid until they
 * expire. This filter closes that window by checking the database on each
 * authenticated request, which makes the database the authoritative gate.
 *
 * <p>Deliberately constructed by {@link SecurityConfig} rather than annotated
 * as a bean: a {@code Filter} bean would also be auto-registered with the
 * servlet container and run a second time outside the security chain.
 */
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING_METHODS = Set.of(
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name()
    );

    private final AccountStatusService accountStatusService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // CORS preflight carries no credentials and must never be rejected.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID userId = authenticatedUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserStatus status = accountStatusService.resolveStatus(userId)
                .orElse(UserStatus.ACTIVE);

        if (status == UserStatus.REMOVED) {
            reject(
                    response,
                    "This account has been removed and can no longer be used."
            );
            return;
        }

        // A suspension pauses participation but still lets the account read,
        // so the client can explain the suspension instead of failing blindly.
        if (status == UserStatus.SUSPENDED
                && STATE_CHANGING_METHODS.contains(request.getMethod())) {
            reject(
                    response,
                    "This account is suspended and cannot make changes."
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private UUID authenticatedUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !jwtAuthentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Not our user id shape; leave it to the endpoint to reject.
            return null;
        }
    }

    private void reject(
            HttpServletResponse response,
            String message
    ) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE
        );
        objectMapper.writeValue(
                response.getOutputStream(),
                RestErrorResponse.builder()
                        .message(message)
                        .code(HttpStatus.FORBIDDEN.value())
                        .status(HttpStatus.FORBIDDEN.getReasonPhrase())
                        .timestamp(Instant.now())
                        .build()
        );
    }
}
