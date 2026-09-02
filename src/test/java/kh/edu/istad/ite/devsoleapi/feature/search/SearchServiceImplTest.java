package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchGroup;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchHit;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MeilisearchClient client;
    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        client = mock(MeilisearchClient.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.indexUid(anyString())).thenAnswer(call -> call.getArgument(0));

        service = new SearchServiceImpl(
                client,
                List.of(new StubIndex("programs"), new StubIndex("users")),
                objectMapper
        );
    }

    @Test
    void listsTheTypesItCanSearchInGroupingOrder() {
        assertEquals(List.of("programs", "users"), service.searchableTypes());
    }

    @Test
    void rejectsATypeItHasNoIndexFor() {
        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.search("acme", "reports", 0, 10)
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    void refusesToServeSearchWhenItIsSwitchedOff() {
        when(client.isEnabled()).thenReturn(false);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.search("acme", null, 0, null)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }

    /**
     * The database could answer something similar with a LIKE, and deliberately
     * is not asked to: results ranked differently and matched differently would
     * still look like search results.
     */
    @Test
    void reportsAnUnreachableMeilisearchRatherThanFallingBack() {
        when(client.search(anyString(), any()))
                .thenThrow(new MeilisearchException("unreachable"));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.search("acme", "programs", 0, 10)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
    }

    @Test
    void countsPagesFromZeroOutsideAndFromOneInside() {
        when(client.search(anyString(), any())).thenReturn(json("""
                {"hits": [], "totalHits": 0, "totalPages": 0}
                """));

        service.search("acme", "programs", 2, 25);

        ArgumentCaptor<Map<String, Object>> query = ArgumentCaptor.captor();
        verify(client).search(anyString(), query.capture());

        assertEquals(Integer.valueOf(3), query.getValue().get("page"));
        assertEquals(Integer.valueOf(25), query.getValue().get("hitsPerPage"));
    }

    @Test
    void rejectsAPageLargerThanTheApiAllows() {
        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.search("acme", "programs", 0, 500)
        );

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    void mapsAHitOntoTheSharedEnvelope() {
        when(client.search(anyString(), any())).thenReturn(json("""
                {
                  "hits": [{
                    "id": "11111111-1111-1111-1111-111111111111",
                    "type": "programs",
                    "slug": "acme-security",
                    "title": "Acme Security",
                    "subtitle": "acme-security",
                    "body": "We run a bounty on our payments platform.",
                    "imageUrl": "https://cdn/acme.png",
                    "minimumBounty": 100,
                    "_formatted": {
                      "body": "We run a <mark>bounty</mark> on our payments"
                    }
                  }],
                  "totalHits": 1,
                  "totalPages": 1
                }
                """));

        SearchResponse response = service.search("bounty", "programs", 0, 20);

        SearchHit hit = response.groups().getFirst().hits().getFirst();
        assertEquals("programs", hit.type());
        assertEquals("acme-security", hit.slug());
        assertEquals("Acme Security", hit.title());
        assertEquals(
                "We run a <mark>bounty</mark> on our payments",
                hit.snippet(),
                "the snippet is the highlighted copy, not the raw body"
        );
        assertEquals("https://cdn/acme.png", hit.imageUrl());
        assertEquals(Integer.valueOf(100), hit.document().get("minimumBounty"));
        assertFalse(
                hit.document().containsKey("_formatted"),
                "the markup sidecar is a duplicate and should not be returned"
        );
        assertEquals(0, response.page().intValue());
        assertEquals(1, response.totalPages().intValue());
        assertEquals(1, response.totalHits());
    }

    @Test
    void fallsBackToTheRawBodyWhenNothingWasHighlighted() {
        when(client.search(anyString(), any())).thenReturn(json("""
                {"hits": [{"id": "x", "body": "plain text"}],
                 "totalHits": 1, "totalPages": 1}
                """));

        SearchResponse response = service.search("", "programs", 0, 20);

        assertEquals(
                "plain text",
                response.groups().getFirst().hits().getFirst().snippet()
        );
    }

    @Test
    void searchesEveryIndexAtOnceWhenNoTypeIsGiven() {
        when(client.multiSearch(any())).thenReturn(json("""
                {"results": [
                  {"indexUid": "programs",
                   "hits": [{"id": "p1", "title": "Acme"}],
                   "totalHits": 7},
                  {"indexUid": "users",
                   "hits": [{"id": "u1", "title": "Acme Maintainer"}],
                   "totalHits": 2}
                ]}
                """));

        SearchResponse response = service.search("acme", null, 0, 5);

        assertEquals(2, response.groups().size());
        assertEquals("programs", response.groups().get(0).type());
        assertEquals(7, response.groups().get(0).totalHits());
        assertEquals("users", response.groups().get(1).type());
        assertEquals(
                9,
                response.totalHits(),
                "the total across a grouped search is the sum of the groups"
        );
        assertNull(response.page(), "there is no single page being read");
        assertNull(response.totalPages());
    }

    @Test
    void leavesOutIndexesThatMatchedNothing() {
        when(client.multiSearch(any())).thenReturn(json("""
                {"results": [
                  {"indexUid": "programs",
                   "hits": [{"id": "p1", "title": "Acme"}],
                   "totalHits": 1},
                  {"indexUid": "users", "hits": [], "totalHits": 0}
                ]}
                """));

        SearchResponse response = service.search("acme", null, 0, 5);

        assertEquals(1, response.groups().size());
        assertEquals("programs", response.groups().getFirst().type());
    }

    /**
     * Results come back in the order the queries went out, but nothing
     * documents that, so a result is labelled by the index it names.
     */
    @Test
    void labelsGroupsByIndexRatherThanByPosition() {
        when(client.multiSearch(any())).thenReturn(json("""
                {"results": [
                  {"indexUid": "users",
                   "hits": [{"id": "u1", "title": "Acme Maintainer"}],
                   "totalHits": 1}
                ]}
                """));

        SearchResponse response = service.search("acme", null, 0, 5);

        SearchGroup group = response.groups().getFirst();
        assertEquals("users", group.type());
        assertEquals("users", group.hits().getFirst().type());
    }

    private JsonNode json(String raw) {
        return objectMapper.readTree(raw);
    }

    private record StubIndex(String name) implements SearchIndexDefinition {

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
            return DocumentBatch.empty();
        }
    }
}
