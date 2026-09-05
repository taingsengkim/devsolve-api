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
    private static final String REASON =
            "Too little detail to tell what the post is about";

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @InjectMocks
    private AutoApprovalHoldNotifier notifier;

    /**
     * The whole point of the feature: a hold about the writing reaches the
     * person who did the writing, with the sentence that decided it.
     */
    @Test
    void anUnclearHoldTellsTheAuthorWhatToDo() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, REASON)
        );

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
        assertTrue(body.getValue().contains(REASON));
    }

    /**
     * An outage is not news the author can use, and saying the check did not
     * run invites them to resubmit in the hope of catching it working. The
     * verdict is still stored, so an author who goes looking is told plainly.
     */
    @Test
    void aHoldThatWasNotAboutTheSubmissionTellsThemNothing() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.NOT_CHECKED,
                        "The review model was unavailable"
                )
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
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, REASON)
        );

        verifyNoInteractions(notificationDispatcher);
    }

    /**
     * An unsafe hold gets the reason too.
     *
     * <p>It used to be the one category that did not, on the argument that the
     * model's sentence names what gave an adversarial submission away. It is
     * included now because the same sentence is the only thing an honestly
     * mislabelled post — a penetration test write-up read as an attack — gives
     * its author to work with, and no hold publishes anything either way: a
     * person still reads it.
     */
    @Test
    void anUnsafeHoldSaysWhatTheCheckSaid() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.UNSAFE,
                        "Reads as an attack on a third party"
                )
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
        // Still no coaching on how to get past this one: the wording says what
        // happened, and only the check's own sentence is added to it.
        assertFalse(body.getValue().contains("fuller description"));
        assertTrue(body.getValue().contains("set aside by the automatic check"));
        assertTrue(body.getValue()
                .contains("Reads as an attack on a third party"));
    }

    /**
     * A verdict the model gave no wording for still reads as a sentence, rather
     * than trailing off into an empty quotation.
     */
    @Test
    void aHoldWithNoReasonStandsOnItsOwn() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, "  ")
        );

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                any(), anyString(), body.capture(), any(), any(), anyString()
        );
        assertFalse(body.getValue().contains("The check's own words"));
        assertTrue(body.getValue().endsWith("all the check needs."));
    }

    @Test
    void anOffTopicHoldSaysSo() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.OFF_TOPIC,
                        "This is a recipe"
                )
        );

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationDispatcher).dispatch(
                any(), anyString(), body.capture(), any(), any(), anyString()
        );
        assertTrue(body.getValue().contains("software or security"));
        assertTrue(body.getValue().contains("This is a recipe"));
    }

    /**
     * The notification is the last thing to happen and the least important. A
     * dead SSE connection or a lost row must not surface as an auto-approval
     * failure, because the decision itself is already made, stored and logged.
     */
    @Test
    void aFailedDeliveryIsSwallowed() {
        doThrow(new IllegalStateException("no connection"))
                .when(notificationDispatcher)
                .dispatch(any(), anyString(), anyString(), any(), any(), anyString());

        notifier.notifyAuthor(
                event(AutoApprovalTarget.PROBLEM),
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, REASON)
        );
    }

    /**
     * Two checks of one submission — what a create followed straight away by a
     * cover upload produces, and what every edit to a queued post produces —
     * must not become two notifications. The key is what the dispatcher dedupes
     * on, so it has to be stable across attempts and across wordings.
     */
    @Test
    void theSameHoldOnTheSamePostKeysTheSameEvent() {
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, REASON)
        );
        notifier.notifyAuthor(
                event(AutoApprovalTarget.SHOWCASE),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.UNCLEAR,
                        "A differently worded sentence about the same hold"
                )
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
