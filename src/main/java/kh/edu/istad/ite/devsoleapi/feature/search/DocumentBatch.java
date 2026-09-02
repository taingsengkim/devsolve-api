package kh.edu.istad.ite.devsoleapi.feature.search;

import java.util.List;
import java.util.UUID;

/**
 * One page of changed rows, already turned into documents.
 *
 * @param next where the following page starts, or null when this was the last
 *             one. Carried rather than inferred from the batch being short,
 *             because a definition orders rows on a key only it knows — the
 *             program index, for one, sorts on the later of a program's own
 *             timestamp and its organization's.
 */
public record DocumentBatch(List<SearchDocument> documents, SyncCursor next) {

    public static DocumentBatch last(List<SearchDocument> documents) {
        return new DocumentBatch(documents, null);
    }

    public static DocumentBatch empty() {
        return new DocumentBatch(List.of(), null);
    }

    /**
     * A page that takes its cursor off the last document in it.
     *
     * <p>Every definition here orders rows by exactly the pair a document
     * carries — the timestamp in {@link SearchDocument#changedAt} and the row
     * id — so the tail document is the boundary, and one row maps to one
     * document whether it is indexed or removed. That is what makes this
     * shareable rather than something each definition works out again.
     *
     * <p>{@code hasMore} is asked for rather than guessed at from the page
     * being short: a definition that cannot produce a cursor from its documents
     * — an empty page that still has rows behind it — has to build the batch
     * itself.
     */
    public static DocumentBatch of(List<SearchDocument> documents, boolean hasMore) {
        if (!hasMore || documents.isEmpty()) {
            return last(documents);
        }
        SearchDocument tail = documents.get(documents.size() - 1);
        return new DocumentBatch(
                documents,
                // Round-trips through the string the document is keyed by,
                // which is where the row's id went on the way in.
                SyncCursor.after(tail.changedAt(), UUID.fromString(tail.id()))
        );
    }

    /**
     * Whether to ask for another page. Note that this is not "the batch had
     * documents in it": a page can legitimately be nothing but removals, or
     * even empty, and stopping there would strand everything behind it.
     */
    public boolean hasMore() {
        return next != null;
    }
}
