package kh.edu.istad.ite.devsoleapi.feature.ai;

import kh.edu.istad.ite.devsoleapi.common.props.GeminiProps;
import kh.edu.istad.ite.devsoleapi.feature.problem.DuplicateJudgements;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.DuplicateVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private static final String GENERATE_URL =
            "https://gemini.test/v1beta/models/gemini-2.5-flash:generateContent";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockRestServiceServer server;
    private GeminiProps props;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        props = new GeminiProps();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setBaseUrl("https://gemini.test");
        props.setModel("gemini-2.5-flash");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder, props, objectMapper);
    }

    @Test
    void staysOffWithoutAKeyHoweverItIsFlagged() {
        props.setApiKey("  ");

        assertFalse(client.isEnabled());
        assertThrows(
                AiUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );
    }

    @Test
    void sendsTheKeyAsAHeaderAndTheSchemaWithTheQuestion() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andExpect(method(HttpMethod.POST))
                // Not ?key=, which would put the secret in access logs and in
                // any exception that quotes the failing URL.
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.system_instruction.parts[0].text")
                        .value("the instructions"))
                .andExpect(jsonPath("$.contents[0].parts[0].text")
                        .value("the draft"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType")
                        .value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type")
                        .value("OBJECT"))
                .andExpect(jsonPath(
                        "$.generationConfig.responseSchema.properties.matches.type"
                ).value("ARRAY"))
                .andRespond(withSuccess(
                        responseOf("{\"matches\":[]}"),
                        MediaType.APPLICATION_JSON
                ));

        client.ask("the instructions", "the draft", DuplicateJudgements.class);

        server.verify();
    }

    @Test
    void readsTheAnswerOutOfTheTextPartItArrivesIn() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withSuccess(
                        responseOf("""
                                {"matches":[{"id":"problem-1",\
                                "verdict":"NEAR_DUPLICATE",\
                                "confidence":72,\
                                "reason":"Same PKCE state loss."}]}"""),
                        MediaType.APPLICATION_JSON
                ));

        DuplicateJudgements judgements =
                client.ask("system", "user", DuplicateJudgements.class);

        assertEquals(1, judgements.safeMatches().size());
        DuplicateJudgements.DuplicateJudgement match =
                judgements.safeMatches().getFirst();
        assertEquals("problem-1", match.id());
        assertEquals(DuplicateVerdict.NEAR_DUPLICATE, match.verdict());
        assertEquals(72, match.confidence());
    }

    /** Long answers arrive split, and the halves are one JSON document. */
    @Test
    void joinsAnAnswerThatCameBackInSeveralParts() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withSuccess(
                        """
                        {"candidates":[{"content":{"parts":[
                          {"text":"{\\"matches\\":["},
                          {"text":"{\\"id\\":\\"problem-1\\",\\"verdict\\":\\"DUPLICATE\\",\\"confidence\\":90,\\"reason\\":\\"Same bug.\\"}"},
                          {"text":"]}"}
                        ]},"finishReason":"STOP"}]}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        DuplicateJudgements judgements =
                client.ask("system", "user", DuplicateJudgements.class);

        assertEquals(
                DuplicateVerdict.DUPLICATE,
                judgements.safeMatches().getFirst().verdict()
        );
    }

    /**
     * The failure this deployment is most likely to meet, since the free tier
     * is what it is pointed at. It has to read like every other failure so the
     * caller falls back instead of surfacing a 429 on somebody's form.
     */
    @Test
    void turnsAQuotaRefusalIntoTheOneFailureCallersHandle() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"Quota exceeded\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        AiUnavailableException failure = assertThrows(
                AiUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );

        assertTrue(failure.getMessage().contains("429"));
        assertTrue(failure.getMessage().contains("Quota exceeded"));
    }

    @Test
    void keepsTheApiKeyOutOfTheMessageItLogs() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"API key not valid: test-key\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        AiUnavailableException failure = assertThrows(
                AiUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );

        assertFalse(failure.getMessage().contains("test-key"));
        assertTrue(failure.getMessage().contains("[redacted]"));
    }

    @Test
    void reportsAPromptTheSafetyFilterRefused() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withSuccess(
                        "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                        MediaType.APPLICATION_JSON
                ));

        AiUnavailableException failure = assertThrows(
                AiUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );

        assertTrue(failure.getMessage().contains("SAFETY"));
    }

    /** A truncated answer is a failed parse, and the reason for it is worth saying. */
    @Test
    void saysWhyWhenTheAnswerRanOutOfRoom() {
        server.expect(once(), requestTo(GENERATE_URL))
                .andRespond(withSuccess(
                        """
                        {"candidates":[{"content":{"parts":[{"text":"{\\"matches\\":[{"}]},\
                        "finishReason":"MAX_TOKENS"}]}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        AiUnavailableException failure = assertThrows(
                AiUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );

        assertTrue(failure.getMessage().contains("MAX_TOKENS"));
    }

    /** Wraps a JSON document as the quoted string Gemini returns it in. */
    private String responseOf(String answer) {
        return """
                {"candidates":[{"content":{"parts":[{"text":%s}],"role":"model"},\
                "finishReason":"STOP"}]}"""
                .formatted(objectMapper.writeValueAsString(answer));
    }
}
