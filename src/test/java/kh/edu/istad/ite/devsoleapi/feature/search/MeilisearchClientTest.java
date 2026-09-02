package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.common.props.MeilisearchProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MeilisearchClientTest {

    private MockRestServiceServer server;
    private MeilisearchProps props;
    private MeilisearchClient client;

    @BeforeEach
    void setUp() {
        props = new MeilisearchProps();
        props.setEnabled(true);
        props.setUrl("http://meili:7700");
        props.setApiKey("master-key");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MeilisearchClient(builder, props);
    }

    @Test
    void addsDocumentsAsAReplaceKeyedByTheDocumentId() {
        server.expect(once(), requestTo(
                        "http://meili:7700/indexes/programs/documents?primaryKey=id"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer master-key"))
                .andExpect(jsonPath("$[0].id").value("program-1"))
                .andRespond(withSuccess(
                        "{\"taskUid\": 1}",
                        MediaType.APPLICATION_JSON
                ));

        client.addOrReplace(
                "programs",
                List.of(Map.of("id", "program-1", "title", "Acme"))
        );

        server.verify();
    }

    @Test
    void writesNothingWhenThereIsNothingToWrite() {
        client.addOrReplace("programs", List.of());
        client.deleteDocuments("programs", List.of());

        // No expectations were set, so any request at all would fail here.
        server.verify();
    }

    @Test
    void createsAnIndexOnlyWhenItIsMissing() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());
        server.expect(once(), requestTo("http://meili:7700/indexes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.uid").value("programs"))
                .andExpect(jsonPath("$.primaryKey").value("id"))
                .andRespond(withSuccess(
                        "{\"taskUid\": 2}",
                        MediaType.APPLICATION_JSON
                ));

        client.ensureIndex("programs");

        server.verify();
    }

    @Test
    void leavesAnIndexThatAlreadyExistsAlone() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"uid\": \"programs\"}",
                        MediaType.APPLICATION_JSON
                ));

        client.ensureIndex("programs");

        // The POST that would create it was never expected, so reaching for it
        // would have failed the test.
        server.verify();
    }

    @Test
    void readsBackTheNewestIndexedTimestamp() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.sort[0]").value("updatedAt:desc"))
                .andExpect(jsonPath("$.limit").value(1))
                .andRespond(withSuccess(
                        """
                        {"hits": [{"updatedAt": 1735689600}]}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        OptionalLong newest = client.newestValue("programs", "updatedAt");

        assertEquals(OptionalLong.of(1735689600L), newest);
        server.verify();
    }

    @Test
    void reportsAnEmptyIndexAsNoTimestampAtAll() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs/search"))
                .andRespond(withSuccess(
                        "{\"hits\": []}",
                        MediaType.APPLICATION_JSON
                ));

        assertTrue(client.newestValue("programs", "updatedAt").isEmpty());
    }

    /**
     * An index that has never been created is the ordinary state on a first
     * run, and asking it for a watermark has to answer "nothing yet" rather
     * than blowing up the whole sync pass.
     */
    @Test
    void treatsAMissingIndexAsAnEmptyOne() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs/search"))
                .andRespond(withResourceNotFound());

        assertTrue(client.newestValue("programs", "updatedAt").isEmpty());
    }

    @Test
    void deletesDocumentsByIdInOneBatch() {
        server.expect(once(), requestTo(
                        "http://meili:7700/indexes/programs/documents/delete-batch"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[\"a\", \"b\"]"))
                .andRespond(withSuccess(
                        "{\"taskUid\": 3}",
                        MediaType.APPLICATION_JSON
                ));

        client.deleteDocuments("programs", List.of("a", "b"));

        server.verify();
    }

    @Test
    void raisesAMeilisearchExceptionOnAnUpstreamFailure() {
        server.expect(once(), requestTo("http://meili:7700/indexes/programs/search"))
                .andRespond(withServerError());

        assertThrows(
                MeilisearchException.class,
                () -> client.search("programs", Map.of("q", "acme"))
        );
    }

    @Test
    void refusesToCallOutAtAllWhenSearchIsDisabled() {
        props.setEnabled(false);

        assertThrows(
                MeilisearchException.class,
                () -> client.search("programs", Map.of("q", "acme"))
        );
        assertFalse(client.isReachable());
        server.verify();
    }

    @Test
    void appliesTheConfiguredPrefixToIndexNames() {
        props.setIndexPrefix("staging_");

        assertEquals("staging_programs", client.indexUid("programs"));
    }

    @Test
    void leavesIndexNamesAloneWithoutAPrefix() {
        assertEquals("programs", client.indexUid("programs"));
    }

    /**
     * A Meilisearch started without a master key rejects requests that carry an
     * Authorization header at all, so a blank key has to mean no header rather
     * than an empty one.
     */
    @Test
    void sendsNoAuthorizationHeaderWhenNoKeyIsConfigured() {
        props.setApiKey("");

        server.expect(once(), requestTo("http://meili:7700/health"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(
                        "{\"status\": \"available\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertTrue(client.isReachable());
        server.verify();
    }
}
