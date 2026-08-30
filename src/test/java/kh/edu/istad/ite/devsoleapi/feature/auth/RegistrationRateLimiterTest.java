package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationRateLimiterTest {

    @Mock
    private RateLimitStore rateLimitStore;

    private RegistrationRateLimiter limiter() {
        return new RegistrationRateLimiter(rateLimitStore);
    }

    private void bindRequestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", address);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowsAnAttemptOnTheBurstLimit() {
        bindRequestFrom("203.0.113.9");
        when(rateLimitStore.recordHit(any(), any()))
                .thenReturn((long) RegistrationRateLimiter.BURST_LIMIT);

        assertDoesNotThrow(() -> limiter().check());
    }

    @Test
    void rejectsTheAttemptAfterTheBurstLimit() {
        bindRequestFrom("203.0.113.9");
        when(rateLimitStore.recordHit(startsWith("register:burst:"), any()))
                .thenReturn(RegistrationRateLimiter.BURST_LIMIT + 1L);

        ResponseStatusException rejected =
                assertThrows(ResponseStatusException.class, () -> limiter().check());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getStatusCode());
    }

    @Test
    void rejectsTheAttemptAfterTheSustainedLimitEvenWhenTheBurstIsFine() {
        bindRequestFrom("203.0.113.9");
        when(rateLimitStore.recordHit(startsWith("register:burst:"), any()))
                .thenReturn(1L);
        when(rateLimitStore.recordHit(startsWith("register:sustained:"), any()))
                .thenReturn(RegistrationRateLimiter.SUSTAINED_LIMIT + 1L);

        assertThrows(ResponseStatusException.class, () -> limiter().check());
    }

    @Test
    void countsAgainstTheForwardedAddressRatherThanTheProxySocket() {
        bindRequestFrom("198.51.100.4, 10.0.0.1");
        when(rateLimitStore.recordHit(any(), any())).thenReturn(1L);

        limiter().check();

        verify(rateLimitStore).recordHit(
                eq("register:burst:198.51.100.4"),
                eq(Duration.ofMinutes(1))
        );
    }

    @Test
    void doesNotLimitWhenNoRequestIsBound() {
        assertDoesNotThrow(() -> limiter().check());

        verify(rateLimitStore, never()).recordHit(any(), any());
    }
}
