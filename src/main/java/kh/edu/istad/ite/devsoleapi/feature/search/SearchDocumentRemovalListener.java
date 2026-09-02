package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.feature.search.indexes.ShowcaseSearchIndex;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseHardDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * The one thing the polling indexer cannot do for itself.
 *
 * <p>{@link SearchIndexSynchronizer} finds work by asking each table what
 * changed since it last looked, which covers every way something stops being
 * public — unpublished, rejected, suspended, soft-deleted — because all of
 * those leave a row behind with a fresh {@code updated_at}. A hard delete does
 * not. The row is gone, no query will ever return it again, and its document
 * would sit in the index until somebody rebuilt by hand.
 *
 * <p>So this listens for exactly that, and nothing else. It is not the
 * beginning of a write-path indexer: adding a push here for changes the poller
 * already catches would mean two mechanisms doing one job, and the quieter one
 * being wrong.
 *
 * <p>After commit, because a rolled-back delete must not take the document with
 * it. Failures are logged and dropped — a stranded document is a wrong search
 * result, not a reason to fail a delete the database has already accepted.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchDocumentRemovalListener {

    private final MeilisearchClient client;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShowcaseHardDeleted(ShowcaseHardDeletedEvent event) {
        if (!client.isEnabled()) {
            return;
        }
        try {
            client.deleteDocuments(
                    client.indexUid(ShowcaseSearchIndex.NAME),
                    List.of(event.showcaseId().toString())
            );
        } catch (MeilisearchException exception) {
            log.warn(
                    "Showcase {} was deleted but could not be removed from the "
                            + "search index; rebuild to clear it: {}",
                    event.showcaseId(),
                    exception.getMessage()
            );
        }
    }
}
