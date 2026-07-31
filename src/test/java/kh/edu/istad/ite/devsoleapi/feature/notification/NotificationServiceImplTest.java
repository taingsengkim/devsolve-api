package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inboxIsAlwaysScopedToAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(true),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository
        ).getMine(true, 0, 20).getContent().getFirst();

        assertEquals(notification.getId(), response.id());
        assertEquals(NotificationType.PROBLEM, response.notifiableType());
    }

    @Test
    void markReadCannotEscapeAuthenticatedUsersInbox() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        authenticate(userId);
        when(notificationRepository.findByIdAndUserId(
                notification.getId(),
                userId
        )).thenReturn(Optional.of(notification));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository
        ).markRead(notification.getId());

        assertTrue(response.read());
        verify(notificationRepository).markRead(
                notification.getId(),
                userId
        );
    }

    private Notification notification(UUID userId) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("New problem published")
                .content("How to secure a REST API")
                .notifiableType(NotificationType.PROBLEM)
                .notifiableId(UUID.randomUUID())
                .eventKey("problem-published:" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, java.util.List.of())
        );
    }
}
