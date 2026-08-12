package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationNotificationServiceTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private CompanyIdentityService companyIdentityService;

    @Test
    void registrationNotifiesEveryAdministrator() {
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        when(companyIdentityService.findUserIdsByRealmRole("ADMIN"))
                .thenReturn(Set.of(firstAdmin, secondAdmin));
        OrganizationLifecycleEvent event = event(
                OrganizationLifecycleEventType.REGISTERED,
                null
        );

        service().deliver(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(
                Collection.class
        );
        verify(notificationDispatcher).dispatchToMany(
                captor.capture(),
                eq("New organization registration"),
                any(),
                eq(NotificationType.ORGANIZATION),
                eq(event.organizationId()),
                any()
        );
        assertEquals(
                Set.of(firstAdmin, secondAdmin),
                Set.copyOf(captor.getValue())
        );
    }

    @Test
    void approvalNotifiesOrganizationOwner() {
        OrganizationLifecycleEvent event = event(
                OrganizationLifecycleEventType.APPROVED,
                null
        );

        service().deliver(event);

        verify(notificationDispatcher).dispatch(
                eq(event.ownerId()),
                eq("Organization approved"),
                any(),
                eq(NotificationType.ORGANIZATION),
                eq(event.organizationId()),
                any()
        );
    }

    @Test
    void rejectionNotificationContainsReviewReason() {
        String reason = "The submitted website could not be verified.";
        OrganizationLifecycleEvent event = event(
                OrganizationLifecycleEventType.REJECTED,
                reason
        );

        service().deliver(event);

        verify(notificationDispatcher).dispatch(
                eq(event.ownerId()),
                any(),
                eq(reason),
                eq(NotificationType.ORGANIZATION),
                eq(event.organizationId()),
                any()
        );
    }

    @Test
    void registrationWithoutAdministratorsDoesNotWriteNotifications() {
        when(companyIdentityService.findUserIdsByRealmRole("ADMIN"))
                .thenReturn(Set.of());

        service().deliver(event(OrganizationLifecycleEventType.REGISTERED, null));

        verifyNoInteractions(notificationDispatcher);
    }

    private OrganizationNotificationService service() {
        return new OrganizationNotificationService(
                notificationDispatcher,
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
