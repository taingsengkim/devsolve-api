package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.common.props.MeilisearchProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The Meilisearch HTTP API, as much of it as this application uses.
 *
 * <p>Hand-written against {@link RestClient} rather than pulled in as the
 * official SDK, for the same reasons {@code VirusTotalClient} is: the surface
 * needed here is nine endpoints, the SDK carries its own JSON stack alongside
 * the one Boot already configures, and the timeouts below are the whole point
 * of the class. An unreachable search engine has to cost this application a
 * couple of seconds, not a held request thread.
 *
 * <p>Every write returns as soon as Meilisearch has accepted the task, which is
 * before the documents are searchable. That is deliberate: indexing is
 * asynchronous there, nothing here waits on a task, and the caller has no user
 * on the other end of it.
 */
@Component
@Slf4j
public class MeilisearchClient {

    /** Meilisearch's own document id field, and the one every index uses. */
    static final String PRIMARY_KEY = "id";

    private final RestClient restClient;
    private final MeilisearchProps props;

    /**
     * Marked because there are two constructors here, and without it Spring
     * looks for a no-argument one and fails at startup.
     */
    @Autowired
    public MeilisearchClient(MeilisearchProps props) {
        this(RestClient.builder().requestFactory(timeoutFactory(props)), props);
    }

    /**
     * The seam the tests build on: they hand in a builder whose request factory
     * {@code MockRestServiceServer} owns, which is also why the timeouts above
     * are applied on the injected path only.
     */
    MeilisearchClient(RestClient.Builder restClientBuilder, MeilisearchProps props) {
        String baseUrl = props.getUrl() == null || props.getUrl().isBlank()
                ? "http://localhost:7700"
                : props.getUrl().trim();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.props = props;
    }

    /**
     * The JDK factory specifically, and not {@link
     * org.springframework.http.client.SimpleClientHttpRequestFactory}.
     *
     * <p>That one is built on {@code HttpURLConnection}, which has a fixed list
     * of methods and no PATCH among them — it throws {@code ProtocolException:
     * Invalid HTTP method: PATCH} before a socket is ever opened. Meilisearch
     * applies index settings over PATCH, so with that factory every sync pass
     * created its indexes, failed on the settings write, and reported it as
     * "Meilisearch is unreachable" — the engine being perfectly reachable the
     * whole time, and searches against it answering 200.
     *
     * <p>The connect timeout belongs to the {@link HttpClient} and the read
     * timeout to the factory; there is nowhere to set both on one object.
     */
    private static ClientHttpRequestFactory timeoutFactory(MeilisearchProps props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.getReadTimeout());
        return factory;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** The configured name of an index, prefix included. */
    public String indexUid(String name) {
        String prefix = props.getIndexPrefix() == null
                ? ""
                : props.getIndexPrefix().trim();
        return prefix.isEmpty() ? name : prefix + name;
    }

    public boolean isReachable() {
        if (!isEnabled()) {
            return false;
        }
        try {
            JsonNode health = get("/health");
            return "available".equals(health.path("status").asText(""));
        } catch (MeilisearchException exception) {
            return false;
        }
    }

    /**
     * Creates the index if it is not there yet. Idempotent, and cheap enough on
     * the common path — one GET that answers 200 — to run on every sync pass
     * rather than only at startup, which is what lets the API recover on its own
     * from a Meilisearch that was wiped underneath it.
     */
    public void ensureIndex(String uid) {
        try {
            get("/indexes/{uid}", uid);
            return;
        } catch (MeilisearchNotFoundException notFound) {
            log.info("Creating Meilisearch index {}", uid);
        }

        post("/indexes", Map.of("uid", uid, "primaryKey", PRIMARY_KEY));
    }

    public void applySettings(String uid, IndexSettings settings) {
        exchange(() -> restClient.patch()
                .uri("/indexes/{uid}/settings", uid)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(settings.toPayload())
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Adds the documents, replacing any that already carry the same id. Replace
     * and not merge: a document rebuilt from its row is the whole truth about
     * that row, and merging would leave a field that has since been cleared
     * sitting in the index forever.
     */
    public void addOrReplace(String uid, List<Map<String, Object>> documents) {
        if (documents.isEmpty()) {
            return;
        }
        exchange(() -> restClient.post()
                .uri(builder -> builder
                        .path("/indexes/{uid}/documents")
                        .queryParam("primaryKey", PRIMARY_KEY)
                        .build(uid))
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(documents)
                .retrieve()
                .body(JsonNode.class));
    }

    public void deleteDocuments(String uid, List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        exchange(() -> restClient.post()
                .uri("/indexes/{uid}/documents/delete-batch", uid)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ids)
                .retrieve()
                .body(JsonNode.class));
    }

    public void deleteAllDocuments(String uid) {
        exchange(() -> restClient.delete()
                .uri("/indexes/{uid}/documents", uid)
                .headers(this::authorize)
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode search(String uid, Map<String, Object> query) {
        return exchange(() -> restClient.post()
                .uri("/indexes/{uid}/search", uid)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(query)
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * One round trip for a query that has to hit several indexes, which is what
     * the type-less "search everything" mode is. Sequential per-index searches
     * would answer the same thing at five times the latency.
     */
    public JsonNode multiSearch(List<Map<String, Object>> queries) {
        return exchange(() -> restClient.post()
                .uri("/multi-search")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("queries", queries))
                .retrieve()
                .body(JsonNode.class));
    }

    public long documentCount(String uid) {
        try {
            return get("/indexes/{uid}/stats", uid)
                    .path("numberOfDocuments")
                    .asLong(0L);
        } catch (MeilisearchNotFoundException notFound) {
            return 0L;
        }
    }

    /**
     * The largest {@code updatedAt} in the index, which is how far the last run
     * of this application got before it stopped. Empty when the index holds
     * nothing, which reads as "index everything".
     *
     * @param sortField the numeric field to take the maximum of. Has to be in
     *                  the index's {@code sortableAttributes} or Meilisearch
     *                  rejects the query outright.
     */
    public OptionalLong newestValue(String uid, String sortField) {
        JsonNode response;
        try {
            response = search(uid, Map.of(
                    "q", "",
                    "limit", 1,
                    "sort", List.of(sortField + ":desc"),
                    "attributesToRetrieve", List.of(sortField)
            ));
        } catch (MeilisearchException exception) {
            // Not just a missing index. Settings are applied asynchronously, so
            // for a moment after one is created the field it was just told to
            // sort by is not sortable yet and this comes back 400 — which used
            // to abort the whole pass and leave the index empty until the next
            // one.
            //
            // Failing soft is right for every case that lands here, because
            // "cannot tell" and "index is empty" call for the same thing:
            // index everything. Writes are keyed by document id, so doing that
            // when it was not strictly needed costs a pass, not correctness.
            // Anything genuinely wrong with the connection surfaces a sentence
            // later on the first write.
            log.debug(
                    "No watermark readable from index {} ({}); starting from "
                            + "the beginning",
                    uid,
                    exception.getMessage()
            );
            return OptionalLong.empty();
        }

        JsonNode newest = response.path("hits").path(0).path(sortField);
        return newest.isNumber()
                ? OptionalLong.of(newest.asLong())
                : OptionalLong.empty();
    }

    private void authorize(HttpHeaders headers) {
        String apiKey = props.getApiKey() == null ? "" : props.getApiKey().trim();
        if (!apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }
    }

    private JsonNode get(String uri, Object... variables) {
        return exchange(() -> restClient.get()
                .uri(uri, variables)
                .headers(this::authorize)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode post(String uri, Object body) {
        return exchange(() -> restClient.post()
                .uri(uri)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode exchange(UpstreamCall call) {
        if (!isEnabled()) {
            throw new MeilisearchException("Meilisearch is not enabled");
        }
        try {
            JsonNode response = call.execute();
            return response == null ? NullNode.getInstance() : response;
        } catch (RestClientResponseException exception) {
            throw failureOf(exception);
        } catch (RestClientException exception) {
            // Carry the cause into the message. Everything that is not an HTTP
            // response lands here, and not all of it is a network problem — an
            // unsupported method looks identical from the outside. A log line
            // reading only "unreachable", about an engine that is answering
            // searches perfectly well, sends whoever reads it after the wrong
            // thing entirely.
            throw new MeilisearchException(
                    "Meilisearch request failed: " + rootCauseOf(exception),
                    exception
            );
        }
    }

    /**
     * The innermost cause's type and message — the line that actually says what
     * went wrong. Spring wraps a transport failure two or three deep, and the
     * outer layers only repeat that a request failed.
     */
    private static String rootCauseOf(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank()
                ? root.getClass().getSimpleName()
                : root.getClass().getSimpleName() + ": " + message;
    }

    /**
     * A missing index gets its own type because two callers treat it as an
     * ordinary answer rather than a failure: {@link #ensureIndex} creates it,
     * and {@link #newestValue} reads it as an empty index.
     */
    private MeilisearchException failureOf(RestClientResponseException exception) {
        String detail = messageOf(exception);
        if (exception.getStatusCode().value() == 404) {
            return new MeilisearchNotFoundException(detail);
        }
        return new MeilisearchException(
                "Meilisearch returned " + exception.getStatusCode().value()
                        + ": " + detail
        );
    }

    /**
     * Meilisearch answers every error with the same JSON envelope, and it reads
     * well enough as-is that parsing it would only risk losing it. Truncated
     * because this ends up in a log line.
     */
    private String messageOf(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getStatusText();
        }
        String trimmed = body.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    static class MeilisearchNotFoundException extends MeilisearchException {

        MeilisearchNotFoundException(String message) {
            super(message);
        }
    }

    @FunctionalInterface
    private interface UpstreamCall {
        JsonNode execute();
    }
}
