package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchGroup;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchHit;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /**
     * The same ceiling {@code PageableValidator} puts on every other listing.
     * Meilisearch would happily return more; this is about what the API
     * promises, not what it can do.
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Lower in the grouped mode, because the cost there is multiplied by the
     * number of indexes and nothing renders twenty of each anyway.
     */
    private static final int MAX_GROUPED_SIZE = 20;

    /** Matches the {@code @PageableDefault} on the other public listings. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int DEFAULT_GROUPED_SIZE = 5;

    /** Words of context kept around a match in the body. */
    private static final int CROP_LENGTH = 40;

    private static final String HIGHLIGHT_OPEN = "<mark>";
    private static final String HIGHLIGHT_CLOSE = "</mark>";

    /** Meilisearch's sidecar object holding the highlighted copies of fields. */
    private static final String FORMATTED = "_formatted";

    private final MeilisearchClient client;
    private final List<SearchIndexDefinition> definitions;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> searchableTypes() {
        return definitions.stream()
                .map(SearchIndexDefinition::name)
                .toList();
    }

    @Override
    public SearchResponse search(String query, String type, int page, Integer size) {
        if (!client.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Search is not enabled on this deployment"
            );
        }

        String text = query == null ? "" : query.trim();
        try {
            return type == null || type.isBlank()
                    ? searchEveryIndex(text, groupedSize(size))
                    : searchOneIndex(
                            text,
                            definitionFor(type),
                            Math.max(page, 0),
                            pageSize(size)
                    );
        } catch (MeilisearchException exception) {
            // Nothing to fall back to. The database can answer "programs whose
            // name contains this", which is not the question that was asked —
            // handing back worse results dressed as the real ones would be
            // less honest than saying search is down.
            log.warn("Search failed: {}", exception.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Search is temporarily unavailable"
            );
        }
    }

    private SearchResponse searchOneIndex(
            String text,
            SearchIndexDefinition definition,
            int page,
            int size
    ) {
        JsonNode response = client.search(
                client.indexUid(definition.name()),
                queryBody(text, page, size)
        );

        long totalHits = response.path("totalHits").asLong(0L);
        SearchGroup group = new SearchGroup(
                definition.name(),
                hitsOf(response, definition.name()),
                totalHits
        );

        return new SearchResponse(
                text,
                List.of(group),
                page,
                size,
                totalHits,
                response.path("totalPages").asInt(0)
        );
    }

    /**
     * One round trip for every index, through Meilisearch's multi-search. The
     * results come back in the order the queries went out, but they are matched
     * up by {@code indexUid} rather than by position — position is not something
     * the API documents, and getting it wrong would label every hit with the
     * wrong type.
     */
    private SearchResponse searchEveryIndex(String text, int size) {
        List<Map<String, Object>> queries = new ArrayList<>();
        Map<String, String> namesByUid = new LinkedHashMap<>();

        for (SearchIndexDefinition definition : definitions) {
            String uid = client.indexUid(definition.name());
            namesByUid.put(uid, definition.name());

            Map<String, Object> query = queryBody(text, 0, size);
            query.put("indexUid", uid);
            queries.add(query);
        }

        JsonNode response = client.multiSearch(queries);

        List<SearchGroup> groups = new ArrayList<>();
        for (JsonNode result : response.path("results")) {
            String name = namesByUid.get(result.path("indexUid").asText(""));
            if (name == null) {
                continue;
            }
            List<SearchHit> hits = hitsOf(result, name);
            // An index that matched nothing is left out rather than returned
            // empty: the caller is drawing sections, and a section with no rows
            // in it is one it should not draw.
            if (!hits.isEmpty()) {
                groups.add(new SearchGroup(
                        name,
                        hits,
                        result.path("totalHits").asLong(hits.size())
                ));
            }
        }

        return SearchResponse.grouped(text, groups, size);
    }

    private Map<String, Object> queryBody(String text, int page, int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", text);
        // Meilisearch counts pages from one; every other listing in this API
        // counts from zero, and the endpoint keeps that promise.
        body.put("page", page + 1);
        body.put("hitsPerPage", size);
        body.put("attributesToHighlight", List.of(
                SearchDocuments.TITLE,
                SearchDocuments.SUBTITLE,
                SearchDocuments.BODY
        ));
        body.put("attributesToCrop", List.of(SearchDocuments.BODY));
        body.put("cropLength", CROP_LENGTH);
        body.put("highlightPreTag", HIGHLIGHT_OPEN);
        body.put("highlightPostTag", HIGHLIGHT_CLOSE);
        return body;
    }

    private List<SearchHit> hitsOf(JsonNode result, String type) {
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode hit : result.path("hits")) {
            hits.add(toHit(hit, type));
        }
        return hits;
    }

    @SuppressWarnings("unchecked")
    private SearchHit toHit(JsonNode hit, String type) {
        Map<String, Object> document =
                objectMapper.convertValue(hit, LinkedHashMap.class);
        // Dropped because it is a parallel copy of the same fields with markup
        // in them, and the one piece of it worth having is lifted out below.
        document.remove(FORMATTED);

        JsonNode formatted = hit.path(FORMATTED);
        String snippet = textOf(
                formatted.path(SearchDocuments.BODY),
                textOf(hit.path(SearchDocuments.BODY), null)
        );

        return new SearchHit(
                type,
                textOf(hit.path(SearchDocuments.ID), null),
                textOf(hit.path(SearchDocuments.SLUG), null),
                textOf(hit.path(SearchDocuments.TITLE), null),
                textOf(hit.path(SearchDocuments.SUBTITLE), null),
                snippet,
                textOf(hit.path(SearchDocuments.IMAGE_URL), null),
                document
        );
    }

    private String textOf(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        return node.asText(fallback);
    }

    private SearchIndexDefinition definitionFor(String type) {
        return definitions.stream()
                .filter(definition -> definition.name().equalsIgnoreCase(type.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported search type '" + type + "'. Allowed types: "
                                + String.join(", ", searchableTypes())
                ));
    }

    private int pageSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be <= " + MAX_PAGE_SIZE
            );
        }
        return size;
    }

    private int groupedSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_GROUPED_SIZE;
        }
        if (size > MAX_GROUPED_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be <= " + MAX_GROUPED_SIZE
                            + " when searching every type at once"
            );
        }
        return size;
    }
}
