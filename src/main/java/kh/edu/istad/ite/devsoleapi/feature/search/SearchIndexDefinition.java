package kh.edu.istad.ite.devsoleapi.feature.search;

import java.time.LocalDateTime;

/**
 * Everything the search feature needs to know about one kind of thing it can
 * find. Implementations are Spring beans; the synchronizer picks all of them up
 * by injecting the list, so adding an index is adding a class and nothing else.
 *
 * <p>The two halves of a definition are the settings — what Meilisearch does
 * with the documents — and the loader, which turns rows into documents a page
 * at a time. A page rather than a row so a definition that has to reach for
 * associations (tags, an organization, an author) can do it in one query for
 * the whole page instead of one per document.
 */
public interface SearchIndexDefinition {

    /**
     * The index name without the configured prefix, and also the {@code type}
     * every document in it carries and every search result reports.
     */
    String name();

    IndexSettings settings();

    /**
     * The next {@code size} rows at or after {@code cursor}, oldest change
     * first, as documents.
     *
     * <p>Two obligations on an implementation, and the whole thing is wrong
     * without either. The rows must be ordered by the same key the cursor
     * filters on — otherwise paging skips rows. And the returned batch must
     * carry a {@link SyncCursor} built from the last row of a full page, or
     * null once a page comes back short.
     *
     * @param cursor where to resume. {@link SyncCursor#startingAt} with the
     *               epoch for a full rebuild.
     * @param size   rows per page.
     */
    DocumentBatch loadChangedSince(SyncCursor cursor, int size);
}
