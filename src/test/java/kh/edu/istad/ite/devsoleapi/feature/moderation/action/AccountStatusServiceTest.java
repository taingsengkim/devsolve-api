package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatusServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ModerationActionRepository moderationActionRepository;

    private AccountStatusService service() {
        return new AccountStatusService(
                userProfileRepository,
                moderationActionRepository
        );
    }

    @Test
    void activeAccountIsReturnedWithoutQueryingModerationHistory() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile(UserStatus.ACTIVE)));

        assertEquals(
                Optional.of(UserStatus.ACTIVE),
                service().resolveStatus(userId)
        );
        verify(moderationActionRepository, never())
                .findExpiryTimestamps(any(), any(), any(), any());
    }

    @Test
    void lapsedSuspensionReinstatesTheAccount() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile(UserStatus.SUSPENDED);
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
        stubLatestSuspensionExpiry(
                userId,
                LocalDateTime.now().minusDays(1)
        );

        assertEquals(
                Optional.of(UserStatus.ACTIVE),
                service().resolveStatus(userId)
        );
        assertEquals(UserStatus.ACTIVE, profile.getStatus());
        verify(userProfileRepository).saveAndFlush(profile);
    }

    @Test
    void suspensionStillRunningKeepsTheAccountSuspended() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile(UserStatus.SUSPENDED);
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
        stubLatestSuspensionExpiry(
                userId,
                LocalDateTime.now().plusDays(3)
        );

        assertEquals(
                Optional.of(UserStatus.SUSPENDED),
                service().resolveStatus(userId)
        );
        assertEquals(UserStatus.SUSPENDED, profile.getStatus());
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void suspensionWithNoRecordedExpiryIsNeverLiftedAutomatically() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile(UserStatus.SUSPENDED);
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
        when(moderationActionRepository.findExpiryTimestamps(
                eq(userId),
                eq(ModerationTargetType.USER),
                eq(ModerationActionType.SUSPEND),
                any(Pageable.class)
        )).thenReturn(Collections.singletonList(null));

        assertEquals(
                Optional.of(UserStatus.SUSPENDED),
                service().resolveStatus(userId)
        );
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void removedAccountStaysRemoved() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile(UserStatus.REMOVED)));

        assertEquals(
                Optional.of(UserStatus.REMOVED),
                service().resolveStatus(userId)
        );
    }

    @Test
    void missingProfileYieldsEmptySoNobodyIsLockedOutByMissingData() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertTrue(service().resolveStatus(userId).isEmpty());
    }

    private void stubLatestSuspensionExpiry(
            UUID userId,
            LocalDateTime expiresAt
    ) {
        when(moderationActionRepository.findExpiryTimestamps(
                eq(userId),
                eq(ModerationTargetType.USER),
                eq(ModerationActionType.SUSPEND),
                any(Pageable.class)
        )).thenReturn(List.of(expiresAt));
    }

    private UserProfile profile(UserStatus status) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail("user@example.com");
        profile.setFullName("Test User");
        profile.setStatus(status);
        return profile;
    }
}
