package kh.edu.istad.ite.devsoleapi.feature.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDispatcher notificationDispatcher;

    /**
     * Runs after the publishing transaction commits, so the notification can
     * never undo the thing it is announcing, and never announces something
     * that was rolled back.
     *
     * <p>Failures are logged and swallowed. A user who does not get told their
     * report was triaged has a smaller problem than a user whose report was
     * not triaged, and by this point the triage is already committed —
     * rethrowing would achieve nothing but a stack trace with no listener.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(NotificationEvent event) {
        if (!event.hasRecipients()) {
            return;
        }

        try {
            notificationDispatcher.dispatchToMany(
                    event.recipientIds(),
                    event.title(),
                    event.content(),
                    event.type(),
                    event.notifiableId(),
                    event.eventKey()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to deliver notification {} to {} recipients",
                    event.eventKey(),
                    event.recipientIds().size(),
                    exception
            );
        }
    }
}
