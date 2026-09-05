package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseHardDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Takes a verdict down with the post it was about.
 *
 * <p>Only hard deletes reach here. Everything else on this platform leaves the
 * row in place with a {@code deleted_at} on it, so its verdict still describes
 * something that exists; a showcase deleted outright would otherwise leave its
 * author a verdict in their own list pointing at a page that 404s.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoApprovalCleanupListener {

    private final AutoApprovalRecorder recorder;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShowcaseHardDeleted(ShowcaseHardDeletedEvent event) {
        try {
            recorder.forget(
                    AutoApprovalTarget.SHOWCASE,
                    event.showcaseId()
            );
        } catch (RuntimeException exception) {
            // The showcase is already gone and committed. A verdict left behind
            // is a stale row in one author's list, not a failed delete, and
            // rethrowing from an after-commit listener cannot undo either.
            log.warn(
                    "Could not drop the auto-approval verdict for deleted "
                            + "showcase {}",
                    event.showcaseId(),
                    exception
            );
        }
    }
}
