package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.notification.Notification;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationNotificationService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationRepository notificationRepository;
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
        List<Notification> notifications = adminIds.stream()
                .map(adminId -> notification(
                        adminId,
                        event,
                        title,
                        content
                ))
                .toList();
        notificationRepository.saveAll(notifications);
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
        notificationRepository.save(notification(
                event.ownerId(),
                event,
                title,
                content
        ));
    }

    private Notification notification(
            UUID userId,
            OrganizationLifecycleEvent event,
            String title,
            String content
    ) {
        return Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .notifiableType(NotificationType.ORGANIZATION)
                .notifiableId(event.organizationId())
                .eventKey(eventKey(event))
                .read(false)
                .build();
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
