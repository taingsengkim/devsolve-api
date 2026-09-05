package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tells an author their post is waiting, and why.
 *
 * <p>Without this a hold is indistinguishable from an empty moderation queue.
 * The post says pending, nothing else happens, and the author cannot tell
 * whether something about what they wrote stopped it or whether nobody has
 * looked yet. The approval side has never had that problem — it runs through
 * the same notification a moderator's decision does — so the hold was the one
 * outcome of the three that reached nobody.
 *
 * <p>The wording, including the check's own sentence about the submission,
 * lives in {@link AutoApprovalWording} so that this notification and the verdict
 * the pending post shows say the same thing.
 *
 * <p>Silent on {@link AutoApprovalHold#NOT_CHECKED}. That is not a verdict — it
 * is the check being switched off, out of quota or unreachable, and interrupting
 * an author to tell them the automation did not run describes an operational
 * detail they can do nothing with. It is still recorded by
 * {@link AutoApprovalRecorder}, so an author who goes looking at their own
 * pending post is told plainly that no automatic check stands behind the wait.
 *
 * <p>Dispatched rather than published as a {@code NotificationEvent}: the
 * caller is an after-commit async listener with no transaction of its own, and
 * a transactional event listener published from there never fires at all.
 * {@link NotificationDispatcher} declares {@code REQUIRES_NEW} for this case.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoApprovalHoldNotifier {

    private final NotificationDispatcher notificationDispatcher;

    public void notifyAuthor(
            ContentSubmittedEvent event,
            AutoApprovalDecision decision
    ) {
        AutoApprovalHold hold = decision.hold();
        if (event.authorId() == null
                || hold == null
                || !hold.isAboutTheSubmission()) {
            return;
        }

        try {
            notificationDispatcher.dispatch(
                    event.authorId(),
                    AutoApprovalWording.heldTitle(event.target()),
                    AutoApprovalWording.message(
                            event.target(),
                            false,
                            hold,
                            event.title(),
                            decision.reason()
                    ),
                    AutoApprovalWording.notificationType(event.target()),
                    event.contentId(),
                    eventKey(event, hold)
            );
        } catch (RuntimeException exception) {
            // An author who is not told their post is queued has a smaller
            // problem than one whose post was not queued, and by here the
            // decision is already made, recorded and logged. Rethrowing would
            // only relabel this as an auto-approval failure in the caller's
            // log.
            log.warn(
                    "Could not tell the author about the hold on {} {}",
                    event.target(),
                    event.contentId(),
                    exception
            );
        }
    }

    /**
     * Keyed on the category rather than on the attempt, so the duplicate check
     * in the dispatcher absorbs a submission that gets checked twice — which is
     * what a create followed immediately by a cover upload produces today, and
     * what an author editing a pending post produces every time they save.
     *
     * <p>The cost is that an edit which does not change the category is not
     * announced again. That is the right trade: the author is already looking at
     * the post they just edited, the stored verdict on it is current, and a
     * notification repeating a sentence they read ten seconds ago is how a
     * useful channel becomes one people mute. A hold that changes category, and
     * an edit that clears the check entirely, both still reach them.
     */
    private String eventKey(
            ContentSubmittedEvent event,
            AutoApprovalHold hold
    ) {
        return AutoApprovalWording.noun(event.target()) + ":"
                + event.contentId() + ":auto-held:" + hold;
    }
}
