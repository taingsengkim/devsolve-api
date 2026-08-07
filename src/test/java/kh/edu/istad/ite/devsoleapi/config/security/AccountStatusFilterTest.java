package kh.edu.istad.ite.devsoleapi.config.security;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.AccountStatusService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusFilterTest {

    @Mock
    private AccountStatusService accountStatusService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void removedAccountIsBlockedEvenOnReads() throws Exception {
        UUID userId = authenticate();
        when(accountStatusService.resolveStatus(userId))
                .thenReturn(Optional.of(UserStatus.REMOVED));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = invoke("GET", "/api/v1/problems", chain);

        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest(), "request must not reach the app");
        assertTrue(response.getContentAsString().contains("removed"));
        assertEquals("application/json", response.getHeader("Content-Type"));
    }

    @Test
    void suspendedAccountCannotWrite() throws Exception {
        UUID userId = authenticate();
        when(accountStatusService.resolveStatus(userId))
                .thenReturn(Optional.of(UserStatus.SUSPENDED));

        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response =
                    invoke(method, "/api/v1/problems", chain);

            assertEquals(403, response.getStatus(), method + " must be blocked");
            assertNull(chain.getRequest(), method + " must not reach the app");
        }
    }

    @Test
    void suspendedAccountCanStillRead() throws Exception {
        UUID userId = authenticate();
        when(accountStatusService.resolveStatus(userId))
                .thenReturn(Optional.of(UserStatus.SUSPENDED));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response =
                invoke("GET", "/api/v1/user-profiles/me", chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "reads must pass through");
    }

    @Test
    void activeAccountPassesThrough() throws Exception {
        UUID userId = authenticate();
        when(accountStatusService.resolveStatus(userId))
                .thenReturn(Optional.of(UserStatus.ACTIVE));

        MockFilterChain chain = new MockFilterChain();
        invoke("POST", "/api/v1/problems", chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void anonymousRequestIsNotChecked() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        invoke("GET", "/api/v1/problems", chain);

        assertNotNull(chain.getRequest());
        verify(accountStatusService, never()).resolveStatus(any());
    }

    @Test
    void corsPreflightIsNeverRejected() throws Exception {
        authenticate();

        MockFilterChain chain = new MockFilterChain();
        invoke("OPTIONS", "/api/v1/problems", chain);

        assertNotNull(chain.getRequest());
        verify(accountStatusService, never()).resolveStatus(any());
    }

    private MockHttpServletResponse invoke(
            String method,
            String uri,
            MockFilterChain chain
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new AccountStatusFilter(accountStatusService, objectMapper)
                .doFilter(request, response, chain);
        return response;
    }

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
        return userId;
    }
}
