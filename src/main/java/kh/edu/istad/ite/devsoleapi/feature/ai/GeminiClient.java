package kh.edu.istad.ite.devsoleapi.feature.ai;

import kh.edu.istad.ite.devsoleapi.common.props.GeminiProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google's Generative Language API, as much of it as this application uses —
 * which is one endpoint.
 *
 * <p>Hand-written against {@link RestClient} rather than pulled in as the
 * official SDK, for the same reasons {@code MeilisearchClient} and
 * {@code VirusTotalClient} are: the surface needed here is a single POST, an
 * SDK would carry its own JSON stack alongside the one Boot already configures,
 * and the timeouts below are the whole point of the class. A slow model has to
 * cost this application a bounded wait, not a held request thread.
 *
 * <p>The structured answer arrives as a JSON string inside the response rather
 * than as the response — Gemini constrains what the model writes, and what it
 * writes is still text. So there is one parse here, of a document the API has
 * already promised matches the schema it was given.
 */
@Component
@Slf4j
public class GeminiClient implements AiReviewClient {

    private final RestClient restClient;
    private final GeminiProps props;
    private final ObjectMapper objectMapper;

    /**
     * Marked because there are two constructors here, and without it Spring
     * looks for a no-argument one and fails at startup.
     */
    @Autowired
    public GeminiClient(GeminiProps props, ObjectMapper objectMapper) {
        this(
                RestClient.builder().requestFactory(timeoutFactory(props)),
                props,
                objectMapper
        );
    }

    /**
     * The seam the tests build on: they hand in a builder whose request factory
     * {@code MockRestServiceServer} owns, which is also why the timeouts above
     * are applied on the injected path only.
     */
    GeminiClient(
            RestClient.Builder restClientBuilder,
            GeminiProps props,
            ObjectMapper objectMapper
    ) {
        String baseUrl = props.getBaseUrl() == null || props.getBaseUrl().isBlank()
                ? "https://generativelanguage.googleapis.com"
                : props.getBaseUrl().trim();
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * The connect timeout belongs to the {@link HttpClient} and the read
     * timeout to the factory; there is nowhere to set both on one object.
     */
    private static ClientHttpRequestFactory timeoutFactory(GeminiProps props) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.getReadTimeout());
        return factory;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && !apiKey().isEmpty();
    }

    @Override
    public <T> T ask(String system, String user, Class<T> shape) {
        if (!isEnabled()) {
            throw new AiUnavailableException("Gemini is not enabled");
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri(builder -> builder
                            .path("/v1beta/models/{model}:generateContent")
                            .build(props.getModel()))
                    .header("x-goog-api-key", apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(system, user, shape))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            // 429 here is the free tier's quota, which is the failure this
            // deployment is most likely to meet. It reads the same as any
            // other: fall back.
            throw new AiUnavailableException(
                    "Gemini returned " + exception.getStatusCode().value()
                            + ": " + messageOf(exception)
            );
        } catch (RestClientException exception) {
            throw new AiUnavailableException(
                    "Gemini request failed: " + rootCauseOf(exception),
                    exception
            );
        }

        return parse(response, shape);
    }

    private Map<String, Object> requestBody(
            String system,
            String user,
            Class<?> shape
    ) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", GeminiSchemas.of(shape));
        generationConfig.put("temperature", props.getTemperature());
        generationConfig.put("maxOutputTokens", props.getMaxOutputTokens());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "system_instruction",
                Map.of("parts", List.of(Map.of("text", system)))
        );
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", user))
        )));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private <T> T parse(JsonNode response, Class<T> shape) {
        if (response == null) {
            throw new AiUnavailableException("Gemini returned an empty body");
        }

        // A prompt refused outright never reaches candidates at all, and the
        // reason is the only thing in the response worth reporting.
        String blockReason = response.path("promptFeedback")
                .path("blockReason")
                .asText("");
        if (!blockReason.isEmpty()) {
            throw new AiUnavailableException(
                    "Gemini blocked the request: " + blockReason
            );
        }

        JsonNode candidate = response.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");

        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            text.append(part.path("text").asText(""));
        }

        if (text.isEmpty()) {
            throw new AiUnavailableException(
                    "Gemini returned no content (finishReason="
                            + (finishReason.isEmpty() ? "none" : finishReason)
                            + ")"
            );
        }

        try {
            return objectMapper.readValue(text.toString(), shape);
        } catch (JacksonException exception) {
            // Constrained decoding makes this unlikely, with one real cause:
            // the answer ran into maxOutputTokens and stops mid-object. Say so,
            // because the fix is a setting rather than a retry.
            throw new AiUnavailableException(
                    "Gemini returned an answer that did not match the schema "
                            + "(finishReason="
                            + (finishReason.isEmpty() ? "none" : finishReason)
                            + "): " + exception.getOriginalMessage(),
                    exception
            );
        }
    }

    private String apiKey() {
        return props.getApiKey() == null ? "" : props.getApiKey().trim();
    }

    /**
     * Google answers errors with a JSON envelope that reads well enough as-is
     * that parsing it would only risk losing it. Truncated because this ends up
     * in a log line — and clipped of the key, which the envelope sometimes
     * echoes back inside the failing URL.
     */
    private String messageOf(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return exception.getStatusText();
        }
        String trimmed = body.trim().replace(apiKey(), "[redacted]");
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
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
}
