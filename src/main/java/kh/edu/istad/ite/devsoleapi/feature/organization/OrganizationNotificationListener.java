package kh.edu.istad.ite.devsoleapi.feature.organization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationNotificationListener {

    private final OrganizationNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLifecycleEvent(OrganizationLifecycleEvent event) {
        try {
            notificationService.deliver(event);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to deliver organization lifecycle notification for {}",
                    event.organizationId(),
                    exception
            );
        }
    }
}
