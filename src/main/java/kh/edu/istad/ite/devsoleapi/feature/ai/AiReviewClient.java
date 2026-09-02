package kh.edu.istad.ite.devsoleapi.feature.ai;

/**
 * One question to a language model, answered in a shape this application
 * already knows how to read.
 *
 * <p>Narrow on purpose. Everything that makes the duplicate check trustworthy —
 * retrieving the candidates, refusing an id the model invented, rate limiting,
 * caching the verdict, falling back to keyword matches — sits on this side of
 * the interface and does not care who answered. That is what makes the provider
 * a configuration choice rather than a rewrite.
 *
 * <p>One implementation today, {@code GeminiClient}. The interface is here
 * anyway because it has already earned itself once: moving off a different
 * provider touched these three lines and nothing else in the feature. An
 * implementation reports {@link #isEnabled()} false rather than failing to
 * start when it has no key, so "no model configured" is a deployment that
 * works with a weaker answer.
 */
public interface AiReviewClient {

    boolean isEnabled();

    /**
     * @param system  the stable instructions, which providers bill and cache
     *                separately from the question
     * @param user    the question
     * @param shape   a record whose components define the JSON schema the
     *                answer is constrained to, and the type it is parsed into
     * @throws AiUnavailableException for every failure, including being
     *                                switched off. Callers have one recourse
     *                                for all of them.
     */
    <T> T ask(String system, String user, Class<T> shape);
}
