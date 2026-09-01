package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class VirusTotalClient implements VirusTotalGateway {

    private static final String API_KEY_HEADER = "x-apikey";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final boolean enabled;
    private final String apiKey;

    @Autowired
    public VirusTotalClient(
            @Value("${app.virus-total.base-url:https://www.virustotal.com/api/v3}")
            String baseUrl,
            @Value("${app.virus-total.enabled:false}") boolean enabled,
            @Value("${app.virus-total.api-key:}") String apiKey
    ) {
        this(
                RestClient.builder().requestFactory(timeoutFactory()),
                baseUrl,
                enabled,
                apiKey
        );
    }

    /**
     * Applied on the injected path only, because the test constructor is
     * handed a builder whose request factory {@code MockRestServiceServer}
     * already owns.
     *
     * <p>Without these an unresponsive VirusTotal holds the request thread —
     * and, on the attachment paths, the database connection its transaction
     * owns — for as long as the socket stays open.
     */
    private static ClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    VirusTotalClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            boolean enabled,
            String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /**
     * A 404 here is the ordinary answer for content VirusTotal has never been
     * shown, not a failure, so it is the one upstream status that comes back
     * as an empty result rather than an exception.
     */
    @Override
    public Optional<VirusTotalScanResponse> findByHash(String sha256) {
        requireConfigured();
        if (sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/files/{hash}", sha256)
                    .header(API_KEY_HEADER, apiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            return Optional.of(rethrow(exception));
        } catch (RestClientException exception) {
            log.warn("VirusTotal hash lookup could not be completed",
                    exception);
            throw upstreamFailure("VirusTotal is currently unavailable");
        }

        JsonNode attributes = requiredData(response).path("attributes");
        JsonNode analysisStats = attributes.path("last_analysis_stats");

        // A record with no analysis on it yet is the same as no record: there
        // is nothing to decide from, so let the caller submit it properly.
        if (analysisStats.isMissingNode()) {
            return Optional.empty();
        }

        Map<String, Integer> stats = statsOf(analysisStats);

        return Optional.of(new VirusTotalScanResponse(
                sha256,
                "completed",
                verdictOf("completed", stats),
                stats
        ));
    }

    @Override
    public VirusTotalScanResponse submitFile(
            AttachmentValidator.ValidatedAttachment attachment
    ) {
        requireConfigured();

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part(
                        "file",
                        new ByteArrayResource(attachment.content()) {
                            @Override
                            public String getFilename() {
                                return attachment.originalFileName();
                            }
                        }
                )
                .contentType(MediaType.parseMediaType(attachment.mimeType()));

        JsonNode response = exchange(() -> restClient.post()
                .uri("/files")
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(JsonNode.class));

        return submissionOf(response);
    }

    @Override
    public VirusTotalScanResponse submitUrl(String url) {
        requireConfigured();

        LinkedMultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();
        body.add("url", url);

        JsonNode response = exchange(() -> restClient.post()
                .uri("/urls")
                .header(API_KEY_HEADER, apiKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(JsonNode.class));

        return submissionOf(response);
    }

    @Override
    public VirusTotalScanResponse getAnalysis(String analysisId) {
        requireConfigured();
        if (analysisId == null || analysisId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "VirusTotal analysis ID is required"
            );
        }

        JsonNode response = exchange(() -> restClient.get()
                .uri("/analyses/{id}", analysisId)
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(JsonNode.class));

        return analysisOf(response);
    }

    private VirusTotalScanResponse submissionOf(JsonNode response) {
        JsonNode data = requiredData(response);
        String id = text(data, "id");
        String status = data.path("attributes")
                .path("status")
                .asText("queued");

        return new VirusTotalScanResponse(
                id,
                status,
                VirusTotalScanResponse.Verdict.PENDING,
                Map.of()
        );
    }

    private VirusTotalScanResponse analysisOf(JsonNode response) {
        JsonNode data = requiredData(response);
        JsonNode attributes = data.path("attributes");
        String status = attributes.path("status").asText("queued");
        Map<String, Integer> stats = statsOf(attributes.path("stats"));

        return new VirusTotalScanResponse(
                text(data, "id"),
                status,
                verdictOf(status, stats),
                stats
        );
    }

    private Map<String, Integer> statsOf(JsonNode node) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (String name : new String[]{
                "malicious", "suspicious", "harmless", "undetected",
                "timeout", "confirmed-timeout", "failure",
                "type-unsupported"
        }) {
            if (node.has(name)) {
                stats.put(name, node.path(name).asInt());
            }
        }
        return Map.copyOf(stats);
    }

    private VirusTotalScanResponse.Verdict verdictOf(
            String status,
            Map<String, Integer> stats
    ) {
        if (!"completed".equalsIgnoreCase(status)) {
            return VirusTotalScanResponse.Verdict.PENDING;
        }
        if (stats.getOrDefault("malicious", 0) > 0) {
            return VirusTotalScanResponse.Verdict.MALICIOUS;
        }
        if (stats.getOrDefault("suspicious", 0) > 0) {
            return VirusTotalScanResponse.Verdict.SUSPICIOUS;
        }
        return VirusTotalScanResponse.Verdict.CLEAN;
    }

    private JsonNode requiredData(JsonNode response) {
        if (response == null || response.path("data").isMissingNode()) {
            throw upstreamFailure("VirusTotal returned an invalid response");
        }
        return response.path("data");
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw upstreamFailure("VirusTotal returned an invalid response");
        }
        return value;
    }

    private <T> T exchange(UpstreamCall<T> call) {
        try {
            return call.execute();
        } catch (RestClientResponseException exception) {
            return rethrow(exception);
        } catch (RestClientException exception) {
            log.warn("VirusTotal API request could not be completed",
                    exception);
            throw upstreamFailure("VirusTotal is currently unavailable");
        }
    }

    /**
     * Always throws. Declared with a return type so a caller that has to
     * produce a value on this branch can hand the result straight back.
     */
    private <T> T rethrow(RestClientResponseException exception) {
        int upstreamStatus = exception.getStatusCode().value();
        log.warn("VirusTotal API request failed with status {}",
                upstreamStatus);

        if (upstreamStatus == 429) {
            throw new VirusTotalUnavailableException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "VirusTotal rate limit was reached"
            );
        }
        if (upstreamStatus == 404) {
            throw new VirusTotalUnavailableException(
                    HttpStatus.NOT_FOUND,
                    "VirusTotal analysis was not found"
            );
        }
        throw upstreamFailure(
                "VirusTotal could not process the scan request"
        );
    }

    private void requireConfigured() {
        if (!enabled || apiKey.isBlank()) {
            throw new VirusTotalUnavailableException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VirusTotal scanning is not configured"
            );
        }
    }

    private ResponseStatusException upstreamFailure(String message) {
        return new VirusTotalUnavailableException(
                HttpStatus.BAD_GATEWAY,
                message
        );
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }
}
