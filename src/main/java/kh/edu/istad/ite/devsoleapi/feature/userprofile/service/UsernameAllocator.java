package kh.edu.istad.ite.devsoleapi.feature.userprofile.service;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UsernamePolicy;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A free handle for an account that never chose one.
 *
 * <p>Three paths create a profile — public registration, social login and
 * company registration — and only the first asks the person for a username.
 * The other two have to mint one, and a profile saved without one is rejected
 * at flush, so this is not a convenience: it is the difference between those
 * sign-ups working and failing.
 */
@Service
@RequiredArgsConstructor
public class UsernameAllocator {

    private static final int MAX_READABLE_ATTEMPTS = 1000;

    private final UserProfileRepository userProfileRepository;

    /**
     * @param preferred a handle the account already goes by — an identity
     *                  provider's {@code preferred_username}, say. Used when it
     *                  is a legal, unreserved handle, because it is the name
     *                  this person is known by; null or unusable falls through
     *                  to the email.
     * @param email     the address the fallback is derived from, so the result
     *                  is recognisable to its owner rather than a random string
     *                  they log in to find.
     */
    public String allocate(String preferred, String email) {
        String candidate = UsernamePolicy.isValid(preferred)
                && !UsernamePolicy.isReserved(preferred)
                ? preferred
                : UsernamePolicy.suggestFrom(email);

        if (isFree(candidate)) {
            return candidate;
        }
        for (int suffix = 2; suffix < MAX_READABLE_ATTEMPTS; suffix++) {
            String numbered = UsernamePolicy.withSuffix(candidate, suffix);
            if (isFree(numbered)) {
                return numbered;
            }
        }
        // Nothing readable was free. A handle nobody would guess still beats
        // failing a registration that has otherwise succeeded.
        return UsernamePolicy.withSuffix(
                candidate,
                Math.abs(UUID.randomUUID().hashCode() % 100000)
        );
    }

    private boolean isFree(String username) {
        return !UsernamePolicy.isReserved(username)
                && !userProfileRepository.existsByUsernameIgnoreCase(username);
    }
}
