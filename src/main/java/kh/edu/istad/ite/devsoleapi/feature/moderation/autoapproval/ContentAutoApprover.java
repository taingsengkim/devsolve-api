package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.common.content.ProfanityFilter;
import kh.edu.istad.ite.devsoleapi.common.content.ProfanityVerdict;
import kh.edu.istad.ite.devsoleapi.feature.ai.AiUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The gate every auto-approval goes through.
 *
 * <p>Three checks in order of what they cost. The switch is a row lookup, the
 * word list is local, and only what survives both is worth spending a metered
 * model call on.
 *
 * <p>Every path that is not an unambiguous yes ends in a hold, including every
 * failure: switched off, no model configured, out of quota, a malformed answer.
 * That is the same queue the post would have sat in before this existed, so the
 * worst this feature can do when it breaks is nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentAutoApprover {

    /**
     * How sure the model has to be. Set where a coin-flip cannot publish
     * anything: the cost of holding is a moderator reading a post they were
     * going to read anyway, and the cost of publishing wrongly is the post
     * being live while nobody looks at it.
     */
    static final int MINIMUM_CONFIDENCE = 80;

    private final AutoApprovalService settings;
    private final ContentApprovalReviewer reviewer;

    /**
     * @param prose the parts the author wrote in their own words. Pasted stack
     *              traces and payloads are left out by the caller: they are
     *              output rather than writing, and judging somebody by what
     *              their tooling printed is how a security write-up gets held
     *              for the contents of a log line.
     */
    public AutoApprovalDecision decide(
            AutoApprovalTarget target,
            String title,
            String prose
    ) {
        if (!settings.isEnabled(target)) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.NOT_CHECKED,
                    "Auto-approval is off"
            );
        }
        if (!reviewer.isEnabled()) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.NOT_CHECKED,
                    "No review model is configured"
            );
        }

        ProfanityVerdict language = ProfanityFilter.scan(title, prose);
        if (language.isBlocked() || language.isFlagged()) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.UNSAFE,
                    "The word list flagged this submission"
            );
        }

        ContentApprovalVerdict verdict;
        try {
            verdict = reviewer.review(target, title, prose);
        } catch (AiUnavailableException exception) {
            log.info(
                    "Auto-approval held a {}: the model could not answer",
                    target,
                    exception
            );
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.NOT_CHECKED,
                    "The review model was unavailable"
            );
        }

        if (verdict == null) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.NOT_CHECKED,
                    "The review model gave no answer"
            );
        }
        if (!verdict.safe()) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.UNSAFE,
                    reasonOr(verdict, "Held as unsafe for a public page")
            );
        }
        if (!verdict.onTopic()) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.OFF_TOPIC,
                    reasonOr(verdict, "Held as unrelated to software or security")
            );
        }
        if (verdict.confidence() < MINIMUM_CONFIDENCE) {
            return AutoApprovalDecision.hold(
                    AutoApprovalHold.UNCLEAR,
                    "The model was only " + verdict.confidence()
                            + "% sure; held for a moderator"
            );
        }
        return AutoApprovalDecision.approve(reasonOr(
                verdict,
                "Safe and on topic"
        ));
    }

    private String reasonOr(ContentApprovalVerdict verdict, String fallback) {
        return verdict.reason() == null || verdict.reason().isBlank()
                ? fallback
                : verdict.reason().trim();
    }
}
