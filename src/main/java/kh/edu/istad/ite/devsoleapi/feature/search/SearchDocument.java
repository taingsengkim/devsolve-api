package kh.edu.istad.ite.devsoleapi.feature.search;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * One row, as the index should hold it.
 *
 * <p>{@code fields} being null is how a definition says "this row exists but
 * must not be findable" — a draft program, a rejected showcase, a suspended
 * account, anything soft-deleted. The synchronizer turns those into deletions,
 * which is what keeps an unpublish from leaving the old document behind.
 *
 * @param changedAt the row's {@code updated_at}. Carried alongside the document
 *                  rather than read back out of it because a removal has no
 *                  document to read it from, and the watermark has to advance
 *                  past removals too.
 */
public record SearchDocument(
        String id,
        LocalDateTime changedAt,
        Map<String, Object> fields
) {

    public static SearchDocument indexed(
            UUID id,
            LocalDateTime changedAt,
            Map<String, Object> fields
    ) {
        return new SearchDocument(id.toString(), changedAt, fields);
    }

    public static SearchDocument removed(UUID id, LocalDateTime changedAt) {
        return new SearchDocument(id.toString(), changedAt, null);
    }

    public boolean isRemoval() {
        return fields == null;
    }
}
