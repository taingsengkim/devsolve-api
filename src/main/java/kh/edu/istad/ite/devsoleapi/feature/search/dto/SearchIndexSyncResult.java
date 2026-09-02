package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What one pass did to one index.
 *
 * <p>Counts what was handed to Meilisearch, not what it holds afterwards — that
 * is a separate round trip, it lags because indexing there is asynchronous, and
 * {@code GET /api/v1/admin/search} is where to ask for it.
 *
 * @param error null on success. Present rather than thrown: one index failing
 *              is not a reason to hide what the other four did.
 */
public record SearchIndexSyncResult(
        String index,
        long indexed,
        long removed,

        @Schema(nullable = true)
        String error
) {
}
