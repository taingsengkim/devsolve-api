package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UsernamePolicy;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Gives a social-login account the local profile row that
 * {@link AuthServiceImpl#register} would otherwise have created.
 *
 * <p>Signing in through an identity provider never touches
 * {@code /api/v1/auth/register}: Keycloak's first-broker-login flow creates the
 * account itself. Such a user arrives holding a perfectly valid token but with
 * no row in {@code user_profiles}, which fails every endpoint that resolves a
 * profile or holds a foreign key to one. This closes that gap on the first
 * authenticated request.
 *
 * <p>Deliberately does <em>not</em> grant the {@code USER} realm role. A role
 * added here would land in Keycloak but not in the token the caller is already
 * holding, so the request it was meant to authorise would still fail. The role
 * belongs on the identity provider as a hardcoded-role mapper, which applies
 * during the broker flow and is therefore present in the very first token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProvisioningService {

    private static final String COMPANY_AUTHORITY = "ROLE_COMPANY";
    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private static final int MAX_FULL_NAME_LENGTH = 150;
    private static final int MIN_FULL_NAME_LENGTH = 2;
    private static final int MAX_AVATAR_URL_LENGTH = 2048;
    private static final String FALLBACK_FULL_NAME = "DevSolve User";

    private final UserProfileRepository userProfileRepository;

    /**
     * What {@link #ensureProvisioned} did, so a caller that can report a failure
     * to the client is able to tell the skips apart. The filter ignores this;
     * {@code POST /api/v1/auth/social/sync} turns each skip into an error the
     * frontend can act on instead of a log line nobody reads.
     */
    public enum Outcome {
        CREATED,
        ALREADY_PRESENT,
        SKIPPED_PRIVILEGED,
        SKIPPED_NO_EMAIL,
        SKIPPED_UNRECOGNISED_SUBJECT
    }

    /**
     * Creates the caller's profile row when it is missing. A no-op for accounts
     * that already have one, and for company and admin accounts, which are
     * provisioned by their own registration flows and must never be created as
     * a side effect of a request.
     */
    @Transactional
    public Outcome ensureProvisioned(JwtAuthenticationToken authentication) {
        if (isPrivilegedAccount(authentication)) {
            return Outcome.SKIPPED_PRIVILEGED;
        }

        UUID userId = parseUserId(authentication.getToken().getSubject());
        if (userId == null) {
            return Outcome.SKIPPED_UNRECOGNISED_SUBJECT;
        }
        if (userProfileRepository.existsById(userId)) {
            return Outcome.ALREADY_PRESENT;
        }

        Jwt token = authentication.getToken();
        String email = normalizedEmail(token);
        if (email == null) {
            // Without an email there is no valid row to write: the column is
            // non-null and unique. Leave the account unprovisioned so the
            // failure surfaces as a missing profile rather than a broken one.
            log.warn(
                    "Skipping provisioning for {}: token carries no email claim",
                    userId
            );
            return Outcome.SKIPPED_NO_EMAIL;
        }

        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setUsername(allocateUsername(token, email));
        userProfile.setEmail(email);
        userProfile.setFullName(resolveFullName(token, email));
        userProfile.setAvatarUrl(resolveAvatarUrl(token));
        userProfile.setStatus(UserStatus.ACTIVE);
        userProfileRepository.saveAndFlush(userProfile);

        log.info("Provisioned profile for federated user {}", userId);
        return Outcome.CREATED;
    }

    private boolean isPrivilegedAccount(JwtAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority ->
                        COMPANY_AUTHORITY.equalsIgnoreCase(authority)
                                || ADMIN_AUTHORITY.equalsIgnoreCase(authority)
                );
    }

    /**
     * A free handle for an account that never chose one.
     *
     * <p>The provider's own {@code preferred_username} is tried first, since
     * it is the name this person already goes by, and the email local part
     * after it. Neither is guaranteed to be free or even to be a legal handle,
     * so both are run through the policy and then numbered until they are.
     *
     * <p>The user can change it once from their profile without waiting out
     * the cooldown: {@code usernameChangedAt} is left null here, so a handle
     * nobody picked does not count as a change.
     */
    private String allocateUsername(Jwt token, String email) {
        String preferred = token.getClaimAsString("preferred_username");
        String candidate = UsernamePolicy.isValid(preferred)
                && !UsernamePolicy.isReserved(preferred)
                ? preferred
                : UsernamePolicy.suggestFrom(email);

        if (isFree(candidate)) {
            return candidate;
        }
        for (int suffix = 2; suffix < 1000; suffix++) {
            String numbered = UsernamePolicy.withSuffix(candidate, suffix);
            if (isFree(numbered)) {
                return numbered;
            }
        }
        // Nothing readable was free. A handle nobody can guess still beats
        // failing a sign-in that has otherwise succeeded.
        return UsernamePolicy.withSuffix(
                candidate,
                Math.abs(UUID.randomUUID().hashCode() % 100000)
        );
    }

    private boolean isFree(String username) {
        return !UsernamePolicy.isReserved(username)
                && !userProfileRepository.existsByUsernameIgnoreCase(username);
    }

    private String normalizedEmail(Jwt token) {
        String email = token.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Best available display name, in descending order of quality. The column
     * is non-blank and at least two characters, so the chain ends in a constant
     * rather than risking a row the entity validator would reject.
     */
    private String resolveFullName(Jwt token, String email) {
        String composed = join(
                token.getClaimAsString("given_name"),
                token.getClaimAsString("family_name")
        );
        String candidate = firstUsable(
                composed,
                token.getClaimAsString("name"),
                token.getClaimAsString("preferred_username"),
                emailLocalPart(email)
        );

        if (candidate == null) {
            return FALLBACK_FULL_NAME;
        }
        return candidate.length() > MAX_FULL_NAME_LENGTH
                ? candidate.substring(0, MAX_FULL_NAME_LENGTH)
                : candidate;
    }

    private String resolveAvatarUrl(Jwt token) {
        String picture = token.getClaimAsString("picture");
        if (picture == null || picture.isBlank()) {
            return null;
        }
        String trimmed = picture.trim();
        // An over-long URL is not worth failing the whole insert for.
        return trimmed.length() > MAX_AVATAR_URL_LENGTH ? null : trimmed;
    }

    private String join(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        return (first + " " + last).trim();
    }

    private String firstUsable(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.length() >= MIN_FULL_NAME_LENGTH) {
                return trimmed;
            }
        }
        return null;
    }

    private String emailLocalPart(String email) {
        int separator = email.indexOf('@');
        return separator > 0 ? email.substring(0, separator) : email;
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Not our user id shape; leave it to the endpoint to reject.
            return null;
        }
    }
}
