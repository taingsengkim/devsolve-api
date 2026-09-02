package kh.edu.istad.ite.devsoleapi.common.props;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Access to the Claude API, which is what the duplicate check on a problem
 * draft reasons with.
 *
 * <p>Off by default, and off is a working deployment rather than a broken one:
 * every endpoint that reaches for Claude has a keyword-matching answer to fall
 * back on, and says in its response which of the two the caller is looking at.
 * That matters more here than it does for search — this one is metered, so a
 * deployment that has not been given a key, or does not want to spend one,
 * should degrade rather than fail.
 */
@Configuration
@ConfigurationProperties(prefix = "app.claude")
@Getter
@Setter
@NoArgsConstructor
public class ClaudeProps {

    private boolean enabled = false;

    /** An Anthropic API key. Blank switches the integration off however {@link #enabled} is set. */
    private String apiKey = "";

    /**
     * Anything the Messages API accepts. Left as the current flagship because
     * the judgement asked of it — "is this the same bug, told differently?" —
     * is exactly where a weaker model returns confident nonsense, and one call
     * covers a whole draft rather than a keystroke.
     */
    private String model = "claude-opus-5";

    /**
     * Thinking tokens count against this, so it is not the size of the JSON
     * that comes back. Small enough to bound a runaway, large enough that a
     * dozen candidates never truncate.
     */
    private long maxTokens = 4096;

    /**
     * Covers the whole request. A person is waiting on this one — they pressed
     * a button on a form — so it gives up and falls back to keyword matches
     * well before a browser would.
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Retries inside the SDK, which is where a 429 or a 529 is worth one more
     * attempt. Costs latency the user is already spending.
     */
    private int maxRetries = 1;
}
