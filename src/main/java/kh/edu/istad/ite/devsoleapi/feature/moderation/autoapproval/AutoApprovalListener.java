package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the check after the submission has committed, off the request thread.
 *
 * <p>After commit because the reviewer opens its own transaction to publish,
 * and a post that has not been written yet cannot be published. Off the request
 * thread because a metered model call on a free tier is measured in seconds and
 * sometimes in refusals, and none of that belongs between an author pressing
 * submit and the page coming back.
 *
 * <p>Nothing here can fail a submission. It has already been answered.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoApprovalListener {

    private final ContentAutoApprover approver;
    private final ProblemService problemService;
    private final ShowCasesService showCasesService;

    @Async("autoApprovalTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentSubmitted(ContentSubmittedEvent event) {
        try {
            AutoApprovalDecision decision = approver.decide(
                    event.target(),
                    event.title(),
                    event.prose()
            );
            if (!decision.approved()) {
                // Info, not debug. A hold is the outcome that looks identical
                // to the feature being broken — nothing moves, and without a
                // line here there is no way to tell a switched-off check from
                // a listener that never fired.
                log.info(
                        "Auto-approval left {} {} for a moderator: {}",
                        event.target(),
                        event.contentId(),
                        decision.reason()
                );
                return;
            }
            if (publish(event)) {
                log.info(
                        "Auto-approved {} {}: {}",
                        event.target(),
                        event.contentId(),
                        decision.reason()
                );
            }
        } catch (RuntimeException exception) {
            // The post stays in the queue, which is where it already was.
            log.error(
                    "Auto-approval failed for {} {}",
                    event.target(),
                    event.contentId(),
                    exception
            );
        }
    }

    /**
     * @return false when there was nothing left to publish — a moderator got
     *         there first, or the author withdrew it. Both are races this is
     *         expected to lose quietly.
     */
    private boolean publish(ContentSubmittedEvent event) {
        return switch (event.target()) {
            case PROBLEM -> problemService.autoPublish(event.contentId());
            case SHOWCASE -> showCasesService.autoApprove(event.contentId());
        };
    }
}
