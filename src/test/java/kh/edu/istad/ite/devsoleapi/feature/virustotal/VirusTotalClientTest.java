package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class VirusTotalClientTest {

    private MockRestServiceServer server;
    private VirusTotalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VirusTotalClient(
                builder,
                "https://www.virustotal.com/api/v3",
                true,
                "test-api-key"
        );
    }

    @Test
    void submitsAUrlWithTheServerSideApiKey() {
        server.expect(once(), requestTo(
                        "https://www.virustotal.com/api/v3/urls"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-apikey", "test-api-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "data": {
                            "type": "analysis",
                            "id": "url-analysis-id"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VirusTotalScanResponse response = client.submitUrl(
                "https://example.com/path"
        );

        assertEquals("url-analysis-id", response.analysisId());
        assertEquals("queued", response.status());
        assertEquals(
                VirusTotalScanResponse.Verdict.PENDING,
                response.verdict()
        );
        server.verify();
    }

    @Test
    void submitsValidatedFileContent() {
        server.expect(once(), requestTo(
                        "https://www.virustotal.com/api/v3/files"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-apikey", "test-api-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "data": {
                            "type": "analysis",
                            "id": "file-analysis-id"
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VirusTotalScanResponse response = client.submitFile(
                new AttachmentValidator.ValidatedAttachment(
                        "evidence.txt",
                        "txt",
                        "text/plain",
                        "safe evidence".getBytes()
                )
        );

        assertEquals("file-analysis-id", response.analysisId());
        server.verify();
    }

    @Test
    void mapsCompletedAnalysisStatsToMaliciousVerdict() {
        server.expect(once(), requestTo(
                        "https://www.virustotal.com/api/v3/analyses/analysis-id"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-apikey", "test-api-key"))
                .andRespond(withSuccess(
                        """
                        {
                          "data": {
                            "type": "analysis",
                            "id": "analysis-id",
                            "attributes": {
                              "status": "completed",
                              "stats": {
                                "malicious": 2,
                                "suspicious": 1,
                                "harmless": 60,
                                "undetected": 4
                              }
                            }
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VirusTotalScanResponse response = client.getAnalysis("analysis-id");

        assertEquals("completed", response.status());
        assertEquals(
                VirusTotalScanResponse.Verdict.MALICIOUS,
                response.verdict()
        );
        assertEquals(2, response.stats().get("malicious"));
        server.verify();
    }

    @Test
    void rejectsCallsWhenIntegrationIsDisabled() {
        VirusTotalClient disabled = new VirusTotalClient(
                RestClient.builder(),
                "https://www.virustotal.com/api/v3",
                false,
                "test-api-key"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> disabled.submitUrl("https://example.com")
        );

        assertEquals(503, exception.getStatusCode().value());
    }

    @Test
    void preservesVirusTotalRateLimitAs429() {
        server.expect(once(), requestTo(
                        "https://www.virustotal.com/api/v3/analyses/analysis-id"
                ))
                .andRespond(withTooManyRequests());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.getAnalysis("analysis-id")
        );

        assertEquals(429, exception.getStatusCode().value());
        server.verify();
    }
}
