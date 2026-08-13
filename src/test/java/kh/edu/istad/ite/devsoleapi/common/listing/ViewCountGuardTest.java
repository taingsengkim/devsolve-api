package kh.edu.istad.ite.devsoleapi.common.listing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewCountGuardTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void deduplicatesWithinAResourceTypeWithoutCrossResourceCollisions() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("User-Agent", "test-browser");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
        ViewCountGuard guard = new ViewCountGuard();
        UUID targetId = UUID.randomUUID();

        assertTrue(guard.shouldCount("problem", targetId));
        assertFalse(guard.shouldCount("problem", targetId));
        assertTrue(guard.shouldCount("program", targetId));
    }
}
