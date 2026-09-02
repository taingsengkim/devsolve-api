package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.common.props.MeilisearchProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchIndexSynchronizerTest {

    private static final LocalDateTime NEWEST =
            LocalDateTime.of(2026, 3, 1, 12, 0, 0);

    private MeilisearchClient client;
    private MeilisearchProps props;
    private RecordingIndex index;
    private SearchIndexSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        client = mock(MeilisearchClient.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.indexUid(anyString())).thenAnswer(call -> call.getArgument(0));
        when(client.newestValue(anyString(), anyString()))
                .thenReturn(OptionalLong.empty());

        props = new MeilisearchProps();
        props.setEnabled(true);
        props.setBatchSize(2);
        props.setSyncOverlap(Duration.ofSeconds(30));

        index = new RecordingIndex();
        synchronizer = new SearchIndexSynchronizer(
                client,
                props,
                List.of(index),
                Runnable::run
        );
    }

    @Test
    void indexesEverythingTheFirstTimeAnEmptyIndexIsSeen() {
        index.next(List.of(document("a", NEWEST)));

        synchronizer.syncAll();

        assertEquals(
                LocalDateTime.of(1970, 1, 1, 0, 0),
                index.sinceOf(0),
                "an index with nothing in it has to be filled from the start"
        );
        verify(client).ensureIndex("things");
        verify(client).applySettings(eq("things"), any());
    }

    @Test
    void resumesFromTheNewestDocumentAlreadyIndexed() {
        // 2026-03-01T12:00:00Z
        when(client.newestValue("things", "updatedAt"))
                .thenReturn(OptionalLong.of(1772366400L));
        index.next(List.of());

        synchronizer.syncAll();

        assertEquals(
                LocalDateTime.of(2026, 3, 1, 12, 0).minusSeconds(30),
                index.sinceOf(0),
                "a restart resumes from where the last run got to, less the "
                        + "overlap"
        );
    }

    @Test
    void movesTheWatermarkPastTheNewestRowLessTheOverlap() {
        index.next(List.of(document("a", NEWEST)));
        index.next(List.of());

        synchronizer.syncAll();
        synchronizer.syncAll();

        assertEquals(NEWEST.minusSeconds(30), index.sinceOf(1));
    }

    /**
     * The overlap is subtracted from the newest row seen, not from the previous
     * watermark. Subtracting from the watermark would walk it backwards by
     * thirty seconds on every quiet pass until the whole table was being
     * rescanned.
     */
    @Test
    void neverMovesTheWatermarkBackwards() {
        index.next(List.of(document("a", NEWEST)));
        index.next(List.of(document("a", NEWEST)));
        index.next(List.of(document("a", NEWEST)));

        synchronizer.syncAll();
        synchronizer.syncAll();
        synchronizer.syncAll();

        assertEquals(NEWEST.minusSeconds(30), index.sinceOf(1));
        assertEquals(NEWEST.minusSeconds(30), index.sinceOf(2));
    }

    @Test
    void leavesTheWatermarkWhereItWasWhenAPassFails() {
        doThrow(new MeilisearchException("boom"))
                .when(client).addOrReplace(anyString(), any());
        index.next(List.of(document("a", NEWEST)));
        index.next(List.of(document("a", NEWEST)));

        synchronizer.syncAll();
        synchronizer.syncAll();

        assertEquals(
                LocalDateTime.of(1970, 1, 1, 0, 0),
                index.sinceOf(1),
                "work that was never written has to be read again"
        );
    }

    @Test
    void sendsRowsThatAreNoLongerPublicAsDeletions() {
        UUID gone = UUID.randomUUID();
        index.next(List.of(SearchDocument.removed(gone, NEWEST)));

        synchronizer.syncAll();

        verify(client).deleteDocuments("things", List.of(gone.toString()));
    }

    @Test
    void keepsPagingWhileThereIsMore() {
        index.next(List.of(document("a", NEWEST.minusMinutes(2))), true);
        index.next(List.of(document("b", NEWEST)), false);

        synchronizer.syncAll();

        assertEquals(2, index.calls.size());
        assertEquals(
                RecordingIndex.NEXT_PAGE,
                index.calls.get(1).cursor(),
                "the second page is asked for from where the first one ended, "
                        + "not by page number"
        );
        assertEquals(2, index.calls.get(0).size(), "batch size comes from config");
    }

    /**
     * A page can legitimately be nothing but removals, and stopping on an empty
     * document list would strand every page behind it.
     */
    @Test
    void keepsPagingPastAPageThatProducedNoDocuments() {
        index.next(List.of(), true);
        index.next(List.of(document("a", NEWEST)), false);

        synchronizer.syncAll();

        assertEquals(2, index.calls.size());
    }

    @Test
    void doesNothingAtAllWhenSearchIsDisabled() {
        when(client.isEnabled()).thenReturn(false);

        synchronizer.scheduledSync();

        assertTrue(index.calls.isEmpty());
    }

    private SearchDocument document(String key, LocalDateTime changedAt) {
        UUID id = UUID.nameUUIDFromBytes(key.getBytes());
        return SearchDocument.indexed(id, changedAt, Map.of(
                "id", id.toString(),
                "title", key
        ));
    }

    /**
     * A definition that hands back whatever it was queued with and remembers
     * how it was asked — the cursor each call arrives with is what these tests
     * are actually about.
     */
    private static final class RecordingIndex implements SearchIndexDefinition {

        /**
         * The cursor a page with more behind it hands back. Fixed rather than
         * derived from the documents so that a page carrying none of them can
         * still say there is more — which is the case
         * {@link #keepsPagingPastAPageThatProducedNoDocuments} is about.
         */
        static final SyncCursor NEXT_PAGE = SyncCursor.after(
                LocalDateTime.of(2026, 2, 1, 0, 0),
                UUID.nameUUIDFromBytes("next".getBytes())
        );

        private final List<Call> calls = new ArrayList<>();
        private final List<DocumentBatch> queued = new ArrayList<>();

        void next(List<SearchDocument> documents) {
            next(documents, false);
        }

        void next(List<SearchDocument> documents, boolean hasMore) {
            queued.add(hasMore
                    ? new DocumentBatch(documents, NEXT_PAGE)
                    : DocumentBatch.last(documents));
        }

        LocalDateTime sinceOf(int call) {
            return calls.get(call).cursor().changedAt();
        }

        @Override
        public String name() {
            return "things";
        }

        @Override
        public IndexSettings settings() {
            return new IndexSettings(
                    List.of("title"),
                    List.of(),
                    List.of("updatedAt"),
                    List.of()
            );
        }

        @Override
        public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
            calls.add(new Call(cursor, size));
            return calls.size() <= queued.size()
                    ? queued.get(calls.size() - 1)
                    : DocumentBatch.empty();
        }

        private record Call(SyncCursor cursor, int size) {
        }
    }
}
