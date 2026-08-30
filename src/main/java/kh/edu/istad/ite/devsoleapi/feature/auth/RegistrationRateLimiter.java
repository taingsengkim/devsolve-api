package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.web.ClientAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * How fast one address is allowed to create accounts.
 *
 * <p>Registration takes no token and reaches the Keycloak admin API, so
 * uncapped it is the cheapest way to make the identity provider do work.
 *
 * <p>Both limits are per address, which shared networks collapse into one
 * caller. The numbers are set for that: a lecture hall behind one NAT should
 * never reach them, because turning real signups away is worse here than
 * letting a slow script through.
 */
@Component
@RequiredArgsConstructor
public class RegistrationRateLimiter {

    static final Duration BURST_WINDOW = Duration.ofMinutes(1);
    static final int BURST_LIMIT = 5;

    static final Duration SUSTAINED_WINDOW = Duration.ofHours(1);
    static final int SUSTAINED_LIMIT = 40;

    private final RateLimitStore rateLimitStore;

    /**
     * Call before validating the request: the attempt is what costs something,
     * so a rejected payload has to count too or the limit is free to evade.
     */
    void check() {
        String address = ClientAddress.current();
        if (address == null) {
            return;
        }

        if (rateLimitStore.recordHit("register:burst:" + address, BURST_WINDOW)
                > BURST_LIMIT) {
            throw tooMany();
        }
        if (rateLimitStore.recordHit(
                "register:sustained:" + address,
                SUSTAINED_WINDOW
        ) > SUSTAINED_LIMIT) {
            throw tooMany();
        }
    }

    private static ResponseStatusException tooMany() {
        return new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many registration attempts from this network. "
                        + "Try again later."
        );
    }
}
