package kh.edu.istad.ite.devsoleapi.feature.ai;

/**
 * Claude could not answer: switched off, misconfigured, timed out, rate
 * limited, or refused.
 *
 * <p>One type for all of it on purpose. Every caller in this application has
 * the same recourse — serve the answer it would have served without Claude —
 * so telling the cases apart would only give callers a distinction none of them
 * act on. The message carries the detail for the log.
 */
public class ClaudeUnavailableException extends RuntimeException {

    public ClaudeUnavailableException(String message) {
        super(message);
    }

    public ClaudeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
