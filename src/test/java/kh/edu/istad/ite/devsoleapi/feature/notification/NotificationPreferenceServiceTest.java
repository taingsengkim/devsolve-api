package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationPreferenceResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.UpdateNotificationPreferencesRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    private final UUID userId = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationResponseMapper notificationResponseMapper;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyTypeIsListedWithItsDefaultBeforeAnyoneChoosesAnything() {
        authenticate();
        when(preferenceRepository.findByUserId(userId))
                .thenReturn(List.of());

        List<NotificationPreferenceResponse> preferences =
                service().getMyEmailPreferences();

        assertEquals(
                NotificationType.values().length,
                preferences.size()
        );
        assertTrue(emailEnabledFor(preferences, NotificationType.REPORT));
        assertTrue(emailEnabledFor(preferences, NotificationType.REWARD));
        assertFalse(emailEnabledFor(preferences, NotificationType.COMMENT));
        // Invitations have their own mailer; a second copy here would be two
        // emails for one invitation.
        assertFalse(emailEnabledFor(preferences, NotificationType.INVITATION));
    }

    @Test
    void updatingOneTypeLeavesTheRestAlone() {
        authenticate();
        NotificationPreference stored = new NotificationPreference(
                userId,
                NotificationType.COMMENT,
                true
        );
        when(preferenceRepository.findByUserId(userId))
                .thenReturn(List.of(stored));

        List<NotificationPreferenceResponse> preferences = service()
                .updateMyEmailPreferences(
                        new UpdateNotificationPreferencesRequest(
                                Map.of(NotificationType.REPORT, false)
                        )
                );

        // The type that was named is new, so it is inserted...
        ArgumentCaptor<NotificationPreference> savedCaptor =
                ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(savedCaptor.capture());
        assertEquals(NotificationType.REPORT, savedCaptor.getValue().getType());
        assertFalse(savedCaptor.getValue().isEmailEnabled());
        // ...and the choice already on file is neither rewritten nor lost.
        assertTrue(stored.isEmailEnabled());
        assertTrue(emailEnabledFor(preferences, NotificationType.COMMENT));
        assertTrue(emailEnabledFor(preferences, NotificationType.REWARD));
    }

    @Test
    void changingATypeAlreadyOnFileUpdatesTheRowInPlace() {
        authenticate();
        NotificationPreference stored = new NotificationPreference(
                userId,
                NotificationType.REPORT,
                true
        );
        when(preferenceRepository.findByUserId(userId))
                .thenReturn(List.of(stored));

        service().updateMyEmailPreferences(
                new UpdateNotificationPreferencesRequest(
                        Map.of(NotificationType.REPORT, false)
                )
        );

        assertFalse(stored.isEmailEnabled());
        verify(preferenceRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void aStoredChoiceIsWhatTheMailerReads() {
        when(preferenceRepository.findByUserIdAndType(
                userId,
                NotificationType.REPORT
        )).thenReturn(Optional.of(new NotificationPreference(
                userId,
                NotificationType.REPORT,
                false
        )));

        NotificationMailer mailer = new NotificationMailer(
                preferenceRepository,
                null,
                null,
                "",
                "/notifications",
                "/settings/notifications"
        );

        assertFalse(mailer.emailEnabledFor(userId, NotificationType.REPORT));
    }

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(
                notificationRepository,
                notificationResponseMapper,
                preferenceRepository
        );
    }

    private boolean emailEnabledFor(
            List<NotificationPreferenceResponse> preferences,
            NotificationType type
    ) {
        return preferences.stream()
                .filter(preference -> preference.type() == type)
                .findFirst()
                .orElseThrow()
                .emailEnabled();
    }

    private void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
