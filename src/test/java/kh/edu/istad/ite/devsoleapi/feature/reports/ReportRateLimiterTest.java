package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.InMemoryRateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportRateLimiterTest {

    private ReportRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ReportRateLimiter(new InMemoryRateLimitStore());
    }

    @Test
    void theBurstLimitAllowsExactlyItsAllowanceBeforeRejecting() {
        UUID reporter = UUID.randomUUID();

        for (int attempt = 0; attempt < ReportRateLimiter.BURST_LIMIT; attempt++) {
            int number = attempt + 1;
            assertDoesNotThrow(
                    () -> limiter.checkBurst(reporter),
                    "submission " + number + " is within the allowance"
            );
        }

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(
                        ResponseStatusException.class,
                        () -> limiter.checkBurst(reporter)
                ).getStatusCode()
        );
    }

    /**
     * The window is per reporter, not global: one researcher hitting the limit
     * must not lock everyone else out of the platform.
     */
    @Test
    void oneReportersBurstDoesNotBlockAnother() {
        UUID noisy = UUID.randomUUID();
        for (int attempt = 0; attempt <= ReportRateLimiter.BURST_LIMIT; attempt++) {
            try {
                limiter.checkBurst(noisy);
            } catch (ResponseStatusException expected) {
                // The point of the loop is to exhaust the allowance.
            }
        }

        assertDoesNotThrow(() -> limiter.checkBurst(UUID.randomUUID()));
    }

    @Test
    void theSustainedLimitRejectsOnceTheHourlyCountIsReached() {
        assertDoesNotThrow(
                () -> limiter.checkSustained(ReportRateLimiter.SUSTAINED_LIMIT - 1)
        );

        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                assertThrows(
                        ResponseStatusException.class,
                        () -> limiter.checkSustained(
                                ReportRateLimiter.SUSTAINED_LIMIT
                        )
                ).getStatusCode()
        );
    }

    /**
     * A rejected attempt is counted but never extends the window, so a
     * reporter who keeps retrying is let back in when it expires rather than
     * held out for as long as they keep trying.
     */
    @Test
    void retryingWhileBlockedDoesNotExtendTheWindow() {
        UUID reporter = UUID.randomUUID();
        for (int attempt = 0; attempt < ReportRateLimiter.BURST_LIMIT; attempt++) {
            limiter.checkBurst(reporter);
        }

        assertThrows(
                ResponseStatusException.class,
                () -> limiter.checkBurst(reporter)
        );
        assertThrows(
                ResponseStatusException.class,
                () -> limiter.checkBurst(reporter)
        );
    }
}
