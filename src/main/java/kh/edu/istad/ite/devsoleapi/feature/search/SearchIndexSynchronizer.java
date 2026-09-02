package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.common.props.MeilisearchProps;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchIndexSyncResult;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchSyncReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps every index level with the database.
 *
 * <p>Nothing calls this on the write path. There is no hook in
 * {@code ProgramServiceImpl} that pushes a document when a program is
 * published, and that is the design rather than an omission: a program becomes
 * findable or unfindable through create, update, publish, pause, submit,
 * approve, reject, soft-delete and — indirectly — through its organization
 * being suspended, and an indexer wired into all of those would be one missed
 * call away from a permanently wrong index, silently. Polling {@code updated_at}
 * instead has one code path, catches every one of those cases including the
 * ones nobody thought of, and repairs itself: whatever went wrong, the next pass
 * fixes it.
 *
 * <p>What that costs is freshness. A change is searchable within a sync
 * interval, not immediately. For a search index over public listings that is
 * the right trade — nobody publishes a program and then searches for it to
 * check — and it is the reason the endpoints that must be immediately correct,
 * like fetching a program by handle, still read PostgreSQL.
 *
 * <p>The watermark lives in memory, seeded at startup from the newest document
 * in each index. Restarting therefore resumes rather than rebuilds, and an
 * index that has been wiped underneath the API refills on its own.
 */
@Component
@Slf4j
public class SearchIndexSynchronizer {

    /**
     * Where a rebuild starts. Not {@link LocalDateTime#MIN}, which is year
     * -999999999 and outside what a Postgres {@code timestamp} can even be
     * compared against; no row here predates the epoch.
     */
    private static final LocalDateTime BEGINNING =
            LocalDateTime.of(1970, 1, 1, 0, 0);

    /**
     * Paging stops here regardless. A rebuild is meant to run to the end of the
     * table, so this is not a budget — it is the thing that turns a definition
     * whose {@code hasMore} never goes false into a log line rather than a
     * thread that never returns.
     */
    private static final int MAX_PAGES = 100_000;

    private final MeilisearchClient client;
    private final MeilisearchProps props;
    private final List<SearchIndexDefinition> definitions;
    private final TaskExecutor searchIndexTaskExecutor;

    /**
     * Written out rather than generated: the executor has to be picked by name
     * out of the three this application defines, and Lombok does not carry
     * {@code @Qualifier} through to the constructor it writes.
     */
    public SearchIndexSynchronizer(
            MeilisearchClient client,
            MeilisearchProps props,
            List<SearchIndexDefinition> definitions,
            @Qualifier("searchIndexTaskExecutor") TaskExecutor searchIndexTaskExecutor
    ) {
        this.client = client;
        this.props = props;
        this.definitions = definitions;
        this.searchIndexTaskExecutor = searchIndexTaskExecutor;
    }

    /** Index name to the oldest {@code updated_at} the next pass must re-read. */
    private final Map<String, LocalDateTime> watermarks = new ConcurrentHashMap<>();

    /**
     * Indexes whose settings have been applied in this process. Emptied by a
     * failure so the next pass tries again — this is how the API survives being
     * started before Meilisearch is.
     */
    private final Map<String, Boolean> prepared = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Hands the work to another thread and returns. {@code @Scheduled} methods
     * share one scheduler thread across the whole application, and a rebuild
     * here would hold it for as long as it takes — stalling the retest expiry
     * sweep and everything else on that timer.
     */
    @Scheduled(
            fixedDelayString = "${app.meilisearch.sync-interval:30s}",
            initialDelayString = "${app.meilisearch.sync-initial-delay:15s}"
    )
    public void scheduledSync() {
        if (!client.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Search index sync still running, skipping this tick");
            return;
        }
        searchIndexTaskExecutor.execute(() -> {
            try {
                syncAll();
            } catch (RuntimeException exception) {
                log.warn("Search index sync failed", exception);
            } finally {
                running.set(false);
            }
        });
    }

    /**
     * Runs a pass over every index and waits for it. Used by the admin
     * endpoints, which want the result rather than a promise of one.
     */
    public SearchSyncReport syncAll() {
        List<SearchIndexSyncResult> results = new ArrayList<>();
        for (SearchIndexDefinition definition : definitions) {
            results.add(syncOne(definition));
        }
        return new SearchSyncReport(results);
    }

    /**
     * Starts a full rebuild in the background and returns whether it started.
     *
     * <p>Not synchronous, and not optionally synchronous. A rebuild reads every
     * row of five tables; on anything but a laptop it outlives the proxy in
     * front of this API, and an administrator would get a 504 for a job that
     * was in fact running fine. The way to watch it is
     * {@code GET /api/v1/admin/search}, which reports the document counts as
     * they climb.
     *
     * @return false when a pass is already in flight, in which case nothing
     *         was started
     */
    public boolean requestRebuild() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        searchIndexTaskExecutor.execute(() -> {
            try {
                SearchSyncReport report = rebuildAll();
                if (report.isComplete()) {
                    log.info("Search index rebuild finished");
                } else {
                    log.warn(
                            "Search index rebuild finished with failures: {}",
                            report.failedIndexes()
                    );
                }
            } catch (RuntimeException exception) {
                log.warn("Search index rebuild failed", exception);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    /**
     * Re-reads every row of every table into the index.
     *
     * <p>Does not empty the indexes first. Every document is keyed by its row's
     * id, so a rebuild overwrites what is there; clearing first would only add
     * a window during which search returns nothing.
     *
     * <p>What it therefore cannot clean up is a document whose row was deleted
     * outright, since no query will ever return that row again. One path in
     * this application does that — a showcase author deleting their own
     * showcase — and it is handled at the source by
     * {@link SearchDocumentRemovalListener}. If that ever misses one, clearing
     * the index in Meilisearch and running this is the way back.
     */
    SearchSyncReport rebuildAll() {
        log.info("Rebuilding every search index from the beginning");
        definitions.forEach(definition ->
                watermarks.put(definition.name(), BEGINNING)
        );
        return syncAll();
    }

    private SearchIndexSyncResult syncOne(SearchIndexDefinition definition) {
        String uid = client.indexUid(definition.name());
        long indexed = 0;
        long removed = 0;

        try {
            prepare(definition, uid);

            LocalDateTime since = watermarkFor(definition, uid);
            SyncCursor cursor = SyncCursor.startingAt(since);
            LocalDateTime newest = null;

            for (int page = 0; page < MAX_PAGES; page++) {
                DocumentBatch batch = definition.loadChangedSince(
                        cursor,
                        props.getBatchSize()
                );

                List<Map<String, Object>> upserts = new ArrayList<>();
                List<String> deletions = new ArrayList<>();
                for (SearchDocument document : batch.documents()) {
                    if (document.isRemoval()) {
                        deletions.add(document.id());
                    } else {
                        upserts.add(document.fields());
                    }
                    newest = later(newest, document.changedAt());
                }

                client.addOrReplace(uid, upserts);
                client.deleteDocuments(uid, deletions);
                indexed += upserts.size();
                removed += deletions.size();

                if (!batch.hasMore()) {
                    break;
                }
                cursor = batch.next();
                if (page == MAX_PAGES - 1) {
                    log.warn(
                            "Search index {} stopped at the page cap with more "
                                    + "rows outstanding",
                            uid
                    );
                }
            }

            advanceWatermark(definition, since, newest);

            if (indexed > 0 || removed > 0) {
                log.info(
                        "Search index {}: {} documents written, {} removed",
                        uid,
                        indexed,
                        removed
                );
            }
            return new SearchIndexSyncResult(
                    definition.name(),
                    indexed,
                    removed,
                    null
            );
        } catch (MeilisearchException exception) {
            // The watermark is deliberately left where it was, so whatever this
            // pass did not manage to write is read again next time.
            prepared.remove(definition.name());
            log.warn("Search index {} could not be synced: {}",
                    uid, exception.getMessage());
            return new SearchIndexSyncResult(
                    definition.name(),
                    indexed,
                    removed,
                    exception.getMessage()
            );
        } catch (RuntimeException exception) {
            log.warn("Search index {} could not be built", uid, exception);
            return new SearchIndexSyncResult(
                    definition.name(),
                    indexed,
                    removed,
                    exception.toString()
            );
        }
    }

    /**
     * Creates the index and writes its settings, once per process. Cheap to
     * call — the settings write is a no-op at Meilisearch's end when nothing
     * changed — and repeated after any failure so a restart of Meilisearch does
     * not leave this application writing into an index with default settings.
     */
    private void prepare(SearchIndexDefinition definition, String uid) {
        if (prepared.containsKey(definition.name())) {
            return;
        }
        client.ensureIndex(uid);
        client.applySettings(uid, definition.settings());
        prepared.put(definition.name(), Boolean.TRUE);
    }

    /**
     * Where to resume from. Known in memory after the first pass; before that,
     * taken from the newest document already in the index, which is where the
     * previous run of this application got to.
     */
    private LocalDateTime watermarkFor(SearchIndexDefinition definition, String uid) {
        return watermarks.computeIfAbsent(definition.name(), name -> {
            OptionalLong newest = client.newestValue(
                    uid,
                    SearchDocuments.UPDATED_AT
            );
            if (newest.isEmpty()) {
                log.info("Search index {} is empty, indexing everything", uid);
                return BEGINNING;
            }
            return LocalDateTime
                    .ofEpochSecond(newest.getAsLong(), 0, ZoneOffset.UTC)
                    .minus(props.getSyncOverlap());
        });
    }

    /**
     * Moves the watermark up to the newest row this pass saw, less the overlap.
     *
     * <p>Clamped so it can only ever move forwards. Without the clamp a pass
     * that found nothing newer than it started with would subtract the overlap
     * again, and again, and walk the starting point backwards a little on every
     * tick until it was rescanning the whole table.
     */
    private void advanceWatermark(
            SearchIndexDefinition definition,
            LocalDateTime since,
            LocalDateTime newest
    ) {
        if (newest == null) {
            return;
        }
        LocalDateTime candidate = newest.minus(props.getSyncOverlap());
        watermarks.put(definition.name(), later(since, candidate));
    }

    private LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }
}
