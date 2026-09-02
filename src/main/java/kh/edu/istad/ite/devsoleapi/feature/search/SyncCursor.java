package kh.edu.istad.ite.devsoleapi.feature.search;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Where a sync pass has read up to: the last row it saw, by the key it is
 * ordered on.
 *
 * <p>Paging by this rather than by {@code OFFSET}, which would be shorter to
 * write and quietly wrong. Rows come back ordered by when they changed, so a
 * row updated while a pass is running moves from wherever it was to the end —
 * and every row behind it shifts down one, which under {@code OFFSET} means the
 * next page starts one row late and something in between is never read at all.
 * Nothing would report it: the skipped row keeps its old timestamp, so the
 * watermark moves past it and it stays out of the index until the next time
 * anybody edits it.
 *
 * <p>A cursor is immune to that. It says "after this exact row", so a row
 * moving does not shift the boundary — and it is a range scan rather than a
 * count-and-discard, which is the difference between a rebuild that stays fast
 * on its ten-thousandth page and one that does not.
 *
 * @param id the tie-break, needed because {@code updated_at} is not unique.
 *           Two rows written in the same microsecond would otherwise be free to
 *           swap places between pages, which is the same skip by a different
 *           route.
 */
public record SyncCursor(LocalDateTime changedAt, UUID id) {

    /**
     * Sorts before any generated id, so pairing it with a timestamp means "from
     * that moment, including the rows written exactly at it". The one row it
     * would exclude is one whose id is the nil UUID, which is not something
     * {@code GenerationType.UUID} produces.
     */
    private static final UUID FLOOR = new UUID(0L, 0L);

    public static SyncCursor startingAt(LocalDateTime changedAt) {
        return new SyncCursor(changedAt, FLOOR);
    }

    public static SyncCursor after(LocalDateTime changedAt, UUID id) {
        return new SyncCursor(changedAt, id);
    }
}
