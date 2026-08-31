package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class VirusTotalClient implements VirusTotalGateway {

    private static final String API_KEY_HEADER = "x-apikey";

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
        this(RestClient.builder(), baseUrl, enabled, apiKey);
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
            int upstreamStatus = exception.getStatusCode().value();
            log.warn("VirusTotal API request failed with status {}",
                    upstreamStatus);

            if (upstreamStatus == 429) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "VirusTotal rate limit was reached"
                );
            }
            if (upstreamStatus == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "VirusTotal analysis was not found"
                );
            }
            throw upstreamFailure(
                    "VirusTotal could not process the scan request"
            );
        } catch (RestClientException exception) {
            log.warn("VirusTotal API request could not be completed",
                    exception);
            throw upstreamFailure("VirusTotal is currently unavailable");
        }
    }

    private void requireConfigured() {
        if (!enabled || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VirusTotal scanning is not configured"
            );
        }
    }

    private ResponseStatusException upstreamFailure(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    @FunctionalInterface
    private interface UpstreamCall<T> {
        T execute();
    }
}
