package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import java.util.List;

/**
 * The result of a sync or a rebuild, one line per index.
 *
 * <p>Meilisearch indexes asynchronously, so {@code indexed} counts what this
 * application handed over and had accepted — a moment before any of it is
 * actually searchable.
 */
public record SearchSyncReport(List<SearchIndexSyncResult> indexes) {

    /** Whether every index came through without an error. */
    public boolean isComplete() {
        return indexes.stream().allMatch(result -> result.error() == null);
    }

    public List<String> failedIndexes() {
        return indexes.stream()
                .filter(result -> result.error() != null)
                .map(SearchIndexSyncResult::index)
                .toList();
    }
}
