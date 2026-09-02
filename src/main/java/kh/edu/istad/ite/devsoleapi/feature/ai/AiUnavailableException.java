package kh.edu.istad.ite.devsoleapi.feature.ai;

/**
 * The model could not answer: switched off, misconfigured, timed out, rate
 * limited, over quota, or refused on safety grounds.
 *
 * <p>One type for all of it, and for every provider. Every caller in this
 * application has the same recourse — serve the answer it would have served
 * without a model — so telling the cases apart would only offer a distinction
 * nothing acts on. The message carries the detail for the log.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
