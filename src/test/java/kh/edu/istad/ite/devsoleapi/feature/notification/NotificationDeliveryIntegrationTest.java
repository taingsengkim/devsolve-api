package kh.edu.istad.ite.devsoleapi.feature.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves a published notification actually reaches the database.
 *
 * <p>The unit tests around this mock the dispatcher, so they verify that a
 * service asks for a notification and that the listener forwards it — and both
 * passed while nothing was ever stored. The listener runs after commit, where
 * the publishing transaction has finished but its resources are still bound to
 * the thread; a REQUIRED method joins that finished transaction rather than
 * starting a fresh one, and its writes are dropped silently, with no exception
 * anywhere. Only a real context and a real commit shows it.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationDeliveryIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void aNotificationPublishedInsideATransactionIsStoredAfterItCommits() {
        UUID recipient = UUID.randomUUID();
        String eventKey = "integration:" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(NotificationEvent.to(
                        recipient,
                        "Report triaged",
                        "Your report is now resolved.",
                        NotificationType.REPORT,
                        UUID.randomUUID(),
                        eventKey
                ))
        );

        // dispatchToMany keys per recipient
        assertTrue(
                notificationRepository.existsByUserIdAndEventKey(
                        recipient,
                        eventKey + ":" + recipient
                ),
                "notification was published and committed but never stored"
        );
        assertEquals(1, notificationRepository.countByUserIdAndReadFalse(recipient));
    }

    @Test
    void everyRecipientOfAGroupNotificationIsStored() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String eventKey = "integration:" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new NotificationEvent(
                        List.of(first, second),
                        "New report submitted",
                        "A report is waiting for triage.",
                        NotificationType.REPORT,
                        UUID.randomUUID(),
                        eventKey
                ))
        );

        assertEquals(1, notificationRepository.countByUserIdAndReadFalse(first));
        assertEquals(1, notificationRepository.countByUserIdAndReadFalse(second));
    }

    @Test
    void nothingIsStoredWhenThePublishingTransactionRollsBack() {
        UUID recipient = UUID.randomUUID();
        String eventKey = "integration:" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(NotificationEvent.to(
                    recipient,
                    "Report triaged",
                    "Your report is now resolved.",
                    NotificationType.REPORT,
                    UUID.randomUUID(),
                    eventKey
            ));
            // The triage failed after we asked for the notification. Announcing
            // something that did not happen is worse than announcing nothing.
            status.setRollbackOnly();
        });

        assertEquals(0, notificationRepository.countByUserIdAndReadFalse(recipient));
    }

    @Test
    void publishingTheSameEventTwiceStoresItOnce() {
        UUID recipient = UUID.randomUUID();
        String eventKey = "integration:" + UUID.randomUUID();

        for (int attempt = 0; attempt < 2; attempt++) {
            transactionTemplate.executeWithoutResult(status ->
                    eventPublisher.publishEvent(NotificationEvent.to(
                            recipient,
                            "Report triaged",
                            "Your report is now resolved.",
                            NotificationType.REPORT,
                            UUID.randomUUID(),
                            eventKey
                    ))
            );
        }

        assertEquals(1, notificationRepository.countByUserIdAndReadFalse(recipient));
    }
}
