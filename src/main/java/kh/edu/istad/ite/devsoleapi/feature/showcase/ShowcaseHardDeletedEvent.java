package kh.edu.istad.ite.devsoleapi.feature.showcase;

import java.util.UUID;

/**
 * A showcase row was deleted outright, not soft-deleted.
 *
 * <p>Published because a hard delete is the one change nothing downstream can
 * discover on its own. Everything else here leaves the row in place with a
 * {@code deleted_at} on it, which anything polling {@code updated_at} — the
 * search indexer, for one — reads as "take this out". A row that is gone has no
 * timestamp left to notice.
 */
public record ShowcaseHardDeletedEvent(UUID showcaseId) {
}
