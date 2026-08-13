package kh.edu.istad.ite.devsoleapi.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kh.edu.istad.ite.devsoleapi.feature.auth.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes a federated account real to this API on its first authenticated
 * request, before anything downstream tries to resolve its profile.
 *
 * <p>Runs ahead of {@link AccountStatusFilter} so the moderation gate reads a
 * profile that exists rather than falling through its "no row, nothing to
 * enforce" default.
 *
 * <p>Deliberately constructed by {@link SecurityConfig} rather than annotated
 * as a bean: a {@code Filter} bean would also be auto-registered with the
 * servlet container and run a second time outside the security chain.
 */
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserProvisioningService userProvisioningService;

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

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.isAuthenticated()) {
            provision(jwtAuthentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Provisioning is a repair step, not a gate: a failure here must never turn
     * a working request into a 500. Whatever the caller was doing carries on,
     * and an endpoint that genuinely needs the profile still reports it missing
     * in its own terms.
     */
    private void provision(JwtAuthenticationToken jwtAuthentication) {
        try {
            userProvisioningService.ensureProvisioned(jwtAuthentication);
        } catch (DataIntegrityViolationException exception) {
            // Two first requests raced and the other one won, or this token's
            // email already belongs to another account. The first is the
            // desired end state; the second needs a human, so name both.
            log.warn(
                    "Could not provision profile for {}; a concurrent request "
                            + "or an existing account already holds this identity",
                    jwtAuthentication.getToken().getSubject(),
                    exception
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Unexpected failure provisioning profile for {}",
                    jwtAuthentication.getToken().getSubject(),
                    exception
            );
        }
    }
}
