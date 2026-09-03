package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * What an author is told when the check does not publish their post.
 */
@ExtendWith(MockitoExtension.class)
class AutoApprovalHoldNotifierTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID CONTENT = UUID.randomUUID();
    private static final String TITLE = "Race in the outbox poller";

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private AutoApprovalHoldNotifier notifier;

    /**
     * The whole point of the feature: a hold about the writing reaches the
     * person who did the writing.
     */
    @Test
    void anUnclearHoldTellsTheAuthorWhatToDo() {
        notifier.notifyAuthor(event(AutoApprovalTarget.PROBLEM), AutoApprovalHold.UNCLEAR);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                eq(AUTHOR),
                eq("Your problem is waiting for review"),
                body.capture(),
                eq(NotificationType.PROBLEM),
                eq(CONTENT),
                eq("problem:" + CONTENT + ":auto-held:UNCLEAR")
        );
        assertTrue(body.getValue().contains(TITLE));
        assertTrue(body.getValue().contains("fuller description"));
    }

    /**
     * An outage is not news the author can use, and saying the check did not
     * run invites them to resubmit in the hope of catching it working.
     */
    @Test
    void aHoldThatWasNotAboutTheSubmissionTellsThemNothing() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalHold.NOT_CHECKED
        );

        verifyNoInteractions(notificationDispatcher);
    }

    @Test
    void aMissingAuthorIsNotAnError() {
        notifier.notifyAuthor(
                new ContentSubmittedEvent(
                        AutoApprovalTarget.SHOWCASE,
                        CONTENT,
                        null,
                        TITLE,
                        "prose"
                ),
                AutoApprovalHold.UNCLEAR
        );

        verifyNoInteractions(notificationDispatcher);
    }

    /**
     * The model's own sentence names what gave an adversarial submission away.
     * That belongs in the log, not in a message to its author.
     */
    @Test
    void anUnsafeHoldDoesNotCoachTheAuthor() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalHold.UNSAFE
        );

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                eq(AUTHOR),
                eq("Your showcase is waiting for review"),
                body.capture(),
                eq(NotificationType.SHOWCASE),
                eq(CONTENT),
                eq("showcase:" + CONTENT + ":auto-held:UNSAFE")
        );
        assertFalse(body.getValue().contains("fuller description"));
        assertEquals(
                "\"" + TITLE + "\" was set aside by the automatic check for a"
                        + " moderator to read.",
                body.getValue()
        );
    }

    @Test
    void anOffTopicHoldSaysSo() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalHold.OFF_TOPIC
        );

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                any(), anyString(), body.capture(), any(), any(), anyString()
        );
        assertTrue(body.getValue().contains("software or security"));
    }

    /**
     * The notification is the last thing to happen and the least important. A
     * dead SSE connection or a lost row must not surface as an auto-approval
     * failure, because the decision itself is already made and logged.
     */
    @Test
    void aFailedDeliveryIsSwallowed() {
        doThrow(new IllegalStateException("no connection"))
                .when(notificationDispatcher)
                .dispatch(any(), anyString(), anyString(), any(), any(), anyString());

        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalHold.UNCLEAR
        );
    }

    /**
     * Two checks of one submission — what a create followed straight away by a
     * cover upload produces — must not become two notifications. The key is
     * what the dispatcher dedupes on, so it has to be stable across attempts.
     */
    @Test
    void theSameHoldOnTheSamePostKeysTheSameEvent() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalHold.UNCLEAR
        );
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalHold.UNCLEAR
        );

        // Both attempts carry one key, which is what lets the dispatcher's
        // (user_id, event_key) check collapse them into a single notification.
        verify(notificationDispatcher, times(2)).dispatch(
                eq(AUTHOR),
                anyString(),
                anyString(),
                any(),
                eq(CONTENT),
                eq("showcase:" + CONTENT + ":auto-held:UNCLEAR")
        );
    }

    private ContentSubmittedEvent event(AutoApprovalTarget target) {
        return new ContentSubmittedEvent(target, CONTENT, AUTHOR, TITLE, "prose");
    }
}
