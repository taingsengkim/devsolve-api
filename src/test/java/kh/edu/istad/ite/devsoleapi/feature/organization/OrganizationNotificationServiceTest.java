package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.notification.Notification;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CompanyIdentityService companyIdentityService;

    @Test
    void registrationNotifiesEveryAdministrator() {
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        when(companyIdentityService.findUserIdsByRealmRole("ADMIN"))
                .thenReturn(Set.of(firstAdmin, secondAdmin));
        OrganizationNotificationService service = service();

        service.deliver(event(OrganizationLifecycleEventType.REGISTERED, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(
                List.class
        );
        verify(notificationRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(
                Set.of(firstAdmin, secondAdmin),
                captor.getValue().stream()
                        .map(Notification::getUserId)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void approvalNotifiesOrganizationOwner() {
        OrganizationLifecycleEvent event = event(
                OrganizationLifecycleEventType.APPROVED,
                null
        );

        service().deliver(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(
                Notification.class
        );
        verify(notificationRepository).save(captor.capture());
        Notification notification = captor.getValue();
        assertEquals(event.ownerId(), notification.getUserId());
        assertEquals(NotificationType.ORGANIZATION, notification.getNotifiableType());
        assertEquals("Organization approved", notification.getTitle());
    }

    @Test
    void rejectionNotificationContainsReviewReason() {
        String reason = "The submitted website could not be verified.";

        service().deliver(event(
                OrganizationLifecycleEventType.REJECTED,
                reason
        ));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(
                Notification.class
        );
        verify(notificationRepository).save(captor.capture());
        assertEquals(reason, captor.getValue().getContent());
    }

    @Test
    void registrationWithoutAdministratorsDoesNotWriteNotifications() {
        when(companyIdentityService.findUserIdsByRealmRole("ADMIN"))
                .thenReturn(Set.of());

        service().deliver(event(OrganizationLifecycleEventType.REGISTERED, null));

        verify(notificationRepository, never()).saveAll(any());
    }

    private OrganizationNotificationService service() {
        return new OrganizationNotificationService(
                notificationRepository,
                companyIdentityService
        );
    }

    private OrganizationLifecycleEvent event(
            OrganizationLifecycleEventType type,
            String reason
    ) {
        return new OrganizationLifecycleEvent(
                type,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Acme Security",
                1,
                reason
        );
    }
}
