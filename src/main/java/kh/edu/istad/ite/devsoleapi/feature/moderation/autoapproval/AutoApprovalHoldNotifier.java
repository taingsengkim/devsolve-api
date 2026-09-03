package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationDispatcher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tells an author their post is waiting, and roughly why.
 *
 * <p>Without this a hold is indistinguishable from an empty moderation queue.
 * The post says pending, nothing else happens, and the author cannot tell
 * whether something about what they wrote stopped it or whether nobody has
 * looked yet. The approval side has never had that problem — it runs through
 * the same notification a moderator's decision does — so the hold was the one
 * outcome of the three that reached nobody.
 *
 * <p>What it does not do is repeat the model. The logged reason is written for
 * an operator, and on the adversarial cases it names exactly what gave the
 * submission away, which is a hint nobody gaming the check should be handed.
 * The author gets the category and, where there is one, the thing they can act
 * on.
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
            AutoApprovalHold hold
    ) {
        if (event.authorId() == null
                || hold == null
                || !hold.isAboutTheSubmission()) {
            return;
        }

        try {
            notificationDispatcher.dispatch(
                    event.authorId(),
                    "Your " + noun(event.target()) + " is waiting for review",
                    body(hold, event.title()),
                    type(event.target()),
                    event.contentId(),
                    eventKey(event, hold)
            );
        } catch (RuntimeException exception) {
            // An author who is not told their post is queued has a smaller
            // problem than one whose post was not queued, and by here the
            // decision is already made and logged. Rethrowing would only
            // relabel this as an auto-approval failure in the caller's log.
            log.warn(
                    "Could not tell the author about the hold on {} {}",
                    event.target(),
                    event.contentId(),
                    exception
            );
        }
    }

    /**
     * One sentence, addressed to the person who wrote the post.
     *
     * <p>Only {@link AutoApprovalHold#UNCLEAR} gets advice, because it is the
     * only hold a well-meaning author can clear by writing more. Telling
     * somebody how to get past the other two is coaching, and the moderator has
     * not ruled yet — so the wording says what happened and stops there.
     */
    private String body(AutoApprovalHold hold, String title) {
        String quoted = "\"" + (title == null ? "" : title) + "\"";
        return switch (hold) {
            case UNCLEAR -> quoted
                    + " did not have enough detail for the automatic check to"
                    + " place it, so a moderator will read it instead. A fuller"
                    + " description is usually all the check needs.";
            case OFF_TOPIC -> quoted
                    + " did not look like it was about software or security to"
                    + " the automatic check, so a moderator will read it"
                    + " instead.";
            case UNSAFE -> quoted
                    + " was set aside by the automatic check for a moderator to"
                    + " read.";
            // Never reached: the caller returns early on a hold that says
            // nothing about the submission. Here so that adding a category
            // fails to compile rather than silently picking a wording.
            case NOT_CHECKED -> quoted + " is waiting for a moderator.";
        };
    }

    /**
     * Keyed on the category rather than on the attempt, so the duplicate check
     * in the dispatcher absorbs a submission that gets checked twice — which is
     * what a create followed immediately by a cover upload produces today.
     */
    private String eventKey(
            ContentSubmittedEvent event,
            AutoApprovalHold hold
    ) {
        return noun(event.target()) + ":" + event.contentId()
                + ":auto-held:" + hold;
    }

    private String noun(AutoApprovalTarget target) {
        return switch (target) {
            case PROBLEM -> "problem";
            case SHOWCASE -> "showcase";
        };
    }

    private NotificationType type(AutoApprovalTarget target) {
        return switch (target) {
            case PROBLEM -> NotificationType.PROBLEM;
            case SHOWCASE -> NotificationType.SHOWCASE;
        };
    }
}
