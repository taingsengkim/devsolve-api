package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationNotificationService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationDispatcher notificationDispatcher;
    private final CompanyIdentityService companyIdentityService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(OrganizationLifecycleEvent event) {
        switch (event.type()) {
            case REGISTERED, RESUBMITTED -> notifyAdministrators(event);
            case APPROVED, REJECTED -> notifyOwner(event);
        }
    }

    private void notifyAdministrators(OrganizationLifecycleEvent event) {
        Collection<UUID> adminIds = companyIdentityService
                .findUserIdsByRealmRole(ADMIN_ROLE);
        if (adminIds.isEmpty()) {
            return;
        }
        String title = event.type() == OrganizationLifecycleEventType.REGISTERED
                ? "New organization registration"
                : "Organization registration resubmitted";
        String content = event.organizationName()
                + " is ready for administrator review.";
        notificationDispatcher.dispatchToMany(
                adminIds,
                title,
                content,
                NotificationType.ORGANIZATION,
                event.organizationId(),
                eventKey(event)
        );
    }

    private void notifyOwner(OrganizationLifecycleEvent event) {
        String title;
        String content;
        if (event.type() == OrganizationLifecycleEventType.APPROVED) {
            title = "Organization approved";
            content = event.organizationName()
                    + " is now active on DevSolve.";
        } else {
            title = "Organization registration rejected";
            content = event.reason();
        }
        notificationDispatcher.dispatch(
                event.ownerId(),
                title,
                content,
                NotificationType.ORGANIZATION,
                event.organizationId(),
                eventKey(event)
        );
    }

    private String eventKey(OrganizationLifecycleEvent event) {
        return "organization:"
                + event.organizationId()
                + ":submission:"
                + event.submissionVersion()
                + ":"
                + event.type().name().toLowerCase();
    }
}
