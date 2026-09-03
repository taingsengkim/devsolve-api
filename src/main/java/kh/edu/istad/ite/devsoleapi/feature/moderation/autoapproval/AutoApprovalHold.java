package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * Why a submission was not published automatically, at the granularity the
 * author is told about.
 *
 * <p>Coarser than the sentence that goes in the log, on purpose. The log is for
 * whoever is debugging the automation. This decides what an author reads about
 * their own post, and the model's own wording is not fit for that: it is
 * written to explain a verdict to an operator, and on the adversarial cases it
 * describes exactly what gave the submission away.
 */
public enum AutoApprovalHold {

    /**
     * Nothing was decided about the submission at all — the switch is off, no
     * model is configured, the model could not answer, or its answer was
     * unusable.
     *
     * <p>The author is told nothing here. Their post is sitting in the same
     * queue it would have sat in before this feature existed, and a notice
     * saying the automatic check did not run describes an operational detail
     * they can neither act on nor do anything with.
     */
    NOT_CHECKED,

    /**
     * The model read it and could not place it, usually because there was too
     * little to go on. The one hold an author can actually act on.
     */
    UNCLEAR,

    /** Read as unrelated to software or security. */
    OFF_TOPIC,

    /**
     * Read as something that does not belong on a public page, or caught by the
     * word list before the model was asked.
     */
    UNSAFE;

    /**
     * Whether this hold says anything about what the author wrote, and so
     * whether there is anything worth telling them.
     */
    public boolean isAboutTheSubmission() {
        return this != NOT_CHECKED;
    }
}
