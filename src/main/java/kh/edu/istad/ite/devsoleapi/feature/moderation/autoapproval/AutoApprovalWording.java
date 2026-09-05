package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;

/**
 * Everything the automatic check says to an author, in one place.
 *
 * <p>The same sentence has to reach them twice: once as a notification the
 * moment the check runs, and again on the post itself for as long as it sits
 * pending — a notification is gone once dismissed, and the author looking at
 * their own queued post is exactly the person asking why it is queued. Two
 * copies of this wording would drift, and the pair that drifts is the one where
 * the notification says one thing and the page says another about the same
 * post.
 */
final class AutoApprovalWording {

    private AutoApprovalWording() {
    }

    static String noun(AutoApprovalTarget target) {
        return switch (target) {
            case PROBLEM -> "problem";
            case SHOWCASE -> "showcase";
        };
    }

    static NotificationType notificationType(AutoApprovalTarget target) {
        return switch (target) {
            case PROBLEM -> NotificationType.PROBLEM;
            case SHOWCASE -> NotificationType.SHOWCASE;
        };
    }

    static String heldTitle(AutoApprovalTarget target) {
        return "Your " + noun(target) + " is waiting for review";
    }

    /**
     * One paragraph addressed to the person who wrote the post: what the check
     * decided, and the sentence it decided it in.
     *
     * <p>The model's own reason used to be withheld — it is written to explain a
     * verdict to an operator, and on an adversarial submission it names exactly
     * what gave that submission away. It is included now because on every
     * honest submission, which is nearly all of them, it is the only part an
     * author can act on: "held for a moderator" and "held because it reads as a
     * request to attack a system you do not name having permission for" lead to
     * very different next edits. What it does not do is let anything through —
     * there is no auto-rejection, so a held post is read by a person whatever
     * its author does with this sentence.
     */
    static String message(
            AutoApprovalTarget target,
            boolean approved,
            AutoApprovalHold hold,
            String title,
            String reason
    ) {
        String quoted = "\"" + (title == null ? "" : title) + "\"";
        if (approved) {
            return quoted + " passed the automatic check and is now live.";
        }
        return withReason(heldBody(quoted, target, hold), reason);
    }

    /**
     * @param hold null is read as {@link AutoApprovalHold#NOT_CHECKED} — a
     *             verdict with no category is one that says nothing about the
     *             submission, which is what that category means
     */
    private static String heldBody(
            String quoted,
            AutoApprovalTarget target,
            AutoApprovalHold hold
    ) {
        AutoApprovalHold category = hold == null
                ? AutoApprovalHold.NOT_CHECKED
                : hold;
        return switch (category) {
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
            // The automatic check never ran, so there is nothing about the
            // writing to report. Saying so beats implying a verdict that was
            // never reached.
            case NOT_CHECKED -> quoted
                    + " was not checked automatically, so a moderator will read"
                    + " your " + noun(target) + " before it goes live.";
        };
    }

    private static String withReason(String body, String reason) {
        String trimmed = reason == null ? null : reason.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return body;
        }
        return body + " The check's own words: \"" + trimmed + "\"";
    }
}
