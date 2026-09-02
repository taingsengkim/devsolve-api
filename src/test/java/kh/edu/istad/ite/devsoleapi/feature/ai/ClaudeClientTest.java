package kh.edu.istad.ite.devsoleapi.feature.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import kh.edu.istad.ite.devsoleapi.common.props.ClaudeProps;
import kh.edu.istad.ite.devsoleapi.feature.problem.DuplicateJudgements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeClientTest {

    @Test
    void staysOffWhenNobodyTurnedItOn() {
        ClaudeClient client = new ClaudeClient(new ClaudeProps());

        assertFalse(client.isEnabled());
    }

    @Test
    void staysOffWhenItIsEnabledWithNoKey() {
        ClaudeProps props = new ClaudeProps();
        props.setEnabled(true);

        // Deliberately not a startup failure. A deployment that has not been
        // given a key should serve the fallback, not refuse to boot.
        assertFalse(new ClaudeClient(props).isEnabled());
    }

    @Test
    void refusesToPretendItCanAnswerWhenItIsOff() {
        ClaudeClient client = new ClaudeClient(new ClaudeProps());

        ClaudeUnavailableException failure = assertThrows(
                ClaudeUnavailableException.class,
                () -> client.ask("system", "user", DuplicateJudgements.class)
        );

        assertTrue(failure.getMessage().contains("not enabled"));
    }

    /**
     * The one piece of the integration that can break without anyone touching
     * this repository: the SDK derives the response schema from
     * {@link DuplicateJudgements} by reflection, so a change to that record —
     * or to how the SDK reads one — fails here rather than on the first real
     * request against a paid endpoint.
     *
     * <p>Builds the request without sending it, so it needs no key and no
     * network.
     */
    @Test
    void derivesAResponseSchemaFromTheJudgementRecord() {
        StructuredMessageCreateParams<DuplicateJudgements> params =
                MessageCreateParams.builder()
                        .model("claude-opus-5")
                        .maxTokens(1024L)
                        .addUserMessage("candidates")
                        .outputConfig(DuplicateJudgements.class)
                        .build();

        assertNotNull(params);
    }
}
