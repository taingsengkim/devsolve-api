package kh.edu.istad.ite.devsoleapi.feature.search;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import kh.edu.istad.ite.devsoleapi.common.props.MeilisearchProps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client against a real socket, built the way Spring builds it.
 *
 * <p>{@code MeilisearchClientTest} covers what this client says and how it
 * reads the answers, but it injects a builder whose request factory
 * {@code MockRestServiceServer} owns — so the factory the application actually
 * runs on is never exercised there. That gap hid a real one: the production
 * factory was {@code SimpleClientHttpRequestFactory}, built on
 * {@code HttpURLConnection}, which has no PATCH. Index settings go over PATCH,
 * so in production every sync pass created its indexes, died on the settings
 * write, and reported "Meilisearch is unreachable" about an engine that was
 * answering searches with 200 the entire time.
 *
 * <p>So these go through {@link MeilisearchClient#MeilisearchClient(
 * MeilisearchProps)} — the constructor Spring calls — and assert only that
 * every method this client uses can leave the building.
 */
class MeilisearchClientTransportTest {

    private HttpServer server;
    private MeilisearchClient client;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        MeilisearchProps props = new MeilisearchProps();
        props.setEnabled(true);
        props.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setApiKey("test-master-key");

        // The autowired constructor, so the real request factory is in play.
        client = new MeilisearchClient(props);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /**
     * The regression. Before the fix this threw
     * {@code ProtocolException: Invalid HTTP method: PATCH} from inside the
     * request factory, surfacing as a {@link MeilisearchException}.
     */
    @Test
    void appliesSettingsOverPatch() {
        assertDoesNotThrow(() -> client.applySettings("programs", settings()));

        assertEquals(
                List.of("PATCH /indexes/programs/settings"),
                received,
                "settings are a PATCH, and the request factory has to support it"
        );
    }

    @Test
    void createsAnIndexThatIsNotThereYet() {
        // The GET answers 404 for this uid, which is the path that creates.
        assertDoesNotThrow(() -> client.ensureIndex("missing"));

        assertEquals(
                List.of("GET /indexes/missing", "POST /indexes"),
                received
        );
    }

    @Test
    void writesAndDeletesDocuments() {
        assertDoesNotThrow(() -> client.addOrReplace(
                "programs",
                List.of(Map.of("id", "a"))
        ));
        assertDoesNotThrow(() -> client.deleteDocuments("programs", List.of("a")));
        assertDoesNotThrow(() -> client.deleteAllDocuments("programs"));

        assertEquals(
                List.of(
                        "POST /indexes/programs/documents",
                        "POST /indexes/programs/documents/delete-batch",
                        "DELETE /indexes/programs/documents"
                ),
                received
        );
    }

    @Test
    void searchesAndReadsHealth() {
        assertDoesNotThrow(() -> client.search("programs", Map.of("q", "x")));
        assertDoesNotThrow(() -> client.multiSearch(List.of(Map.of("q", "x"))));
        assertTrue(client.isReachable());

        assertEquals(
                List.of(
                        "POST /indexes/programs/search",
                        "POST /multi-search",
                        "GET /health"
                ),
                received
        );
    }

    private IndexSettings settings() {
        return new IndexSettings(
                List.of("title"),
                List.of(),
                List.of("updatedAt"),
                List.of()
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        received.add(method + " " + path);

        // Drain the body, or the client can see the connection close early.
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }

        boolean missing = "GET".equals(method) && path.equals("/indexes/missing");
        String payload = switch (path) {
            case "/health" -> "{\"status\":\"available\"}";
            default -> missing
                    ? "{\"code\":\"index_not_found\"}"
                    : "{\"taskUid\":1,\"hits\":[]}";
        };

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(missing ? 404 : 202, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
