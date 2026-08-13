package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single source of truth for "may this account act right now?".
 *
 * <p>Suspensions expire lazily: rather than a scheduled sweep, a lapsed
 * suspension is cleared the next time the account is looked at. That keeps the
 * expiry authoritative without a scheduler, and without a window where the
 * database says SUSPENDED but the suspension is already over.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final UserProfileRepository userProfileRepository;
    private final ModerationActionRepository moderationActionRepository;

    /**
     * Current status of an account, reinstating it first if its suspension has
     * lapsed. Empty when no local profile exists for the id — callers should
     * treat that as "nothing to enforce" rather than as a denial, so a missing
     * profile can never lock every user out.
     */
    @Transactional
    public Optional<UserStatus> resolveStatus(UUID userId) {
        Optional<UserProfile> found = userProfileRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        UserProfile profile = found.get();
        if (profile.getStatus() != UserStatus.SUSPENDED) {
            return Optional.of(profile.getStatus());
        }

        if (!hasSuspensionLapsed(userId)) {
            return Optional.of(UserStatus.SUSPENDED);
        }

        profile.setStatus(UserStatus.ACTIVE);
        userProfileRepository.saveAndFlush(profile);
        log.info(
                "Suspension for user {} has expired; account reinstated",
                userId
        );
        return Optional.of(UserStatus.ACTIVE);
    }

    private boolean hasSuspensionLapsed(UUID userId) {
        List<LocalDateTime> expiries = moderationActionRepository
                .findExpiryTimestamps(
                        userId,
                        ModerationTargetType.USER,
                        ModerationActionType.SUSPEND,
                        PageRequest.of(0, 1)
                );

        // No recorded suspension, or one with no expiry, stays in force until
        // an admin reinstates it explicitly.
        if (expiries.isEmpty() || expiries.getFirst() == null) {
            return false;
        }
        return expiries.getFirst().isBefore(LocalDateTime.now());
    }
}
