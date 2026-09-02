package kh.edu.istad.ite.devsoleapi.feature.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import kh.edu.istad.ite.devsoleapi.common.props.ClaudeProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one place this application talks to Claude.
 *
 * <p>Unlike {@code MeilisearchClient} and {@code VirusTotalClient} this is not
 * hand-rolled over {@code RestClient}. The official SDK is what keeps the
 * request shape correct as the API moves, and it carries the piece worth having
 * here: {@code outputConfig(Class)} derives a JSON schema from a record and the
 * response comes back already parsed into it, so a malformed answer is the
 * SDK's problem rather than a parser in this repository.
 *
 * <p>Constructed at most once, and only when there is something to construct.
 * A missing key is not a startup failure — {@link #isEnabled()} answers false
 * and every caller has a keyword-matching path to fall back on.
 */
@Component
@Slf4j
public class ClaudeClient {

    private final ClaudeProps props;

    /** Null when the integration is off, which is what {@link #isEnabled()} reads. */
    private final AnthropicClient client;

    public ClaudeClient(ClaudeProps props) {
        this.props = props;
        this.client = connect(props);
    }

    private static AnthropicClient connect(ClaudeProps props) {
        if (!props.isEnabled()) {
            return null;
        }
        String apiKey = props.getApiKey() == null ? "" : props.getApiKey().trim();
        if (apiKey.isEmpty()) {
            // Worth a line at startup rather than a surprise on the first
            // request: somebody set enabled=true and expects this to work.
            log.warn(
                    "app.claude.enabled is true but no API key is set; "
                            + "Claude-backed features will fall back"
            );
            return null;
        }
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(props.getTimeout())
                .maxRetries(props.getMaxRetries())
                .build();
    }

    public boolean isEnabled() {
        return client != null;
    }

    /**
     * One request, one answer, shaped like {@code shape}.
     *
     * <p>The system prompt carries a cache breakpoint because it is the stable
     * half of every call — the instructions do not move between requests while
     * the draft and its candidates change on every one. Below the minimum
     * cacheable prefix this costs nothing and does nothing.
     *
     * @param shape a record whose components become the JSON schema the model
     *              is constrained to
     * @throws ClaudeUnavailableException for every failure, including being
     *                                    switched off
     */
    public <T> T ask(String system, String user, Class<T> shape) {
        if (client == null) {
            throw new ClaudeUnavailableException("Claude is not enabled");
        }

        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(user)
                .outputConfig(shape)
                .build();

        try {
            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst()
                    .orElseThrow(() -> new ClaudeUnavailableException(
                            "Claude returned no content"
                    ));
        } catch (AnthropicException exception) {
            // Everything the SDK raises — a 4xx, a 5xx, a timeout, a refusal
            // that failed to parse. The caller has one recourse for all of it.
            throw new ClaudeUnavailableException(
                    "Claude request failed: " + exception.getMessage(),
                    exception
            );
        }
    }
}
