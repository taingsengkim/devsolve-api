package kh.edu.istad.ite.devsoleapi.feature.search;

import io.swagger.v3.oas.annotations.Operation;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchIndexStatus;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchRebuildResponse;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operating the search indexes. Behind {@code /api/v1/admin/**}, which the
 * security chain already restricts to administrators.
 *
 * <p>There is deliberately nothing here for indexing a single document. The
 * indexes are kept current by {@link SearchIndexSynchronizer} polling
 * {@code updated_at}, and an endpoint that let one row be pushed by hand would
 * exist only to paper over that not working — which is a thing to fix, not to
 * work around.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class SearchAdminController {

    private final MeilisearchClient client;
    private final SearchIndexSynchronizer synchronizer;
    private final List<SearchIndexDefinition> definitions;

    @Operation(
            summary = "Whether search is configured, answering, and how much it holds"
    )
    @GetMapping
    public SearchStatusResponse status() {
        boolean reachable = client.isReachable();

        List<SearchIndexStatus> indexes = definitions.stream()
                .map(definition -> {
                    String uid = client.indexUid(definition.name());
                    return new SearchIndexStatus(
                            definition.name(),
                            uid,
                            reachable ? countOf(uid) : -1
                    );
                })
                .toList();

        return new SearchStatusResponse(client.isEnabled(), reachable, indexes);
    }

    @Operation(
            summary = "Rebuild every index from the database",
            description = """
                    Returns as soon as the work has been handed to a background
                    thread — a rebuild reads every row of five tables and would
                    outlast the request. Watch `GET /api/v1/admin/search` for
                    the document counts.

                    Answers 409 while a pass is already running.
                    """
    )
    @PostMapping("/reindex")
    public ResponseEntity<SearchRebuildResponse> reindex() {
        if (!client.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SearchRebuildResponse(
                            false,
                            "Search is not enabled on this deployment"
                    ));
        }
        if (!synchronizer.requestRebuild()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new SearchRebuildResponse(
                            false,
                            "A search index pass is already running"
                    ));
        }
        return ResponseEntity.accepted().body(new SearchRebuildResponse(
                true,
                "Rebuilding every search index in the background"
        ));
    }

    private long countOf(String uid) {
        try {
            return client.documentCount(uid);
        } catch (MeilisearchException exception) {
            return -1;
        }
    }
}
