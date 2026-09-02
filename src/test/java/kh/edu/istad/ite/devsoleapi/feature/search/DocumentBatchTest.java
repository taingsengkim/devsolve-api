package kh.edu.istad.ite.devsoleapi.feature.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursor a page hands to the one after it. Getting this wrong does not
 * fail — it silently re-reads a page forever, or skips one.
 */
class DocumentBatchTest {

    private static final LocalDateTime EARLIER =
            LocalDateTime.of(2026, 3, 1, 12, 0);
    private static final LocalDateTime LATER =
            LocalDateTime.of(2026, 3, 1, 12, 5);

    @Test
    void takesTheCursorFromTheLastDocumentOnThePage() {
        SearchDocument tail = indexed(LATER);

        DocumentBatch batch = DocumentBatch.of(
                List.of(indexed(EARLIER), tail),
                true
        );

        assertTrue(batch.hasMore());
        assertEquals(
                new SyncCursor(LATER, UUID.fromString(tail.id())),
                batch.next(),
                "the next page starts after the last row this one read"
        );
    }

    /**
     * A removal is a row like any other. Skipping it when picking the cursor
     * would restart the following page from an older position and read the
     * whole tail of the page again, every pass.
     */
    @Test
    void takesTheCursorFromARemovalJustTheSame() {
        UUID gone = UUID.randomUUID();

        DocumentBatch batch = DocumentBatch.of(
                List.of(indexed(EARLIER), SearchDocument.removed(gone, LATER)),
                true
        );

        assertEquals(new SyncCursor(LATER, gone), batch.next());
    }

    @Test
    void carriesNoCursorWhenThereIsNothingBehindThePage() {
        DocumentBatch batch = DocumentBatch.of(List.of(indexed(LATER)), false);

        assertFalse(batch.hasMore());
        assertNull(batch.next());
    }

    /**
     * Nothing to take a cursor from. A definition that can produce this — an
     * empty page with rows still behind it — has to build the batch itself
     * rather than come through here.
     */
    @Test
    void carriesNoCursorWhenThePageIsEmpty() {
        assertFalse(DocumentBatch.of(List.of(), true).hasMore());
    }

    private SearchDocument indexed(LocalDateTime changedAt) {
        UUID id = UUID.randomUUID();
        return SearchDocument.indexed(id, changedAt, Map.of("id", id.toString()));
    }
}
