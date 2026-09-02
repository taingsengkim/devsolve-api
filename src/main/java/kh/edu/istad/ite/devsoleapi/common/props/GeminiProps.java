package kh.edu.istad.ite.devsoleapi.common.props;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Access to Google's Generative Language API, which is what the duplicate check
 * on a problem draft reasons with by default.
 *
 * <p>Chosen because it has a free tier that needs no card, and the judgement
 * being asked for — "are these two differently worded reports the same bug?" —
 * is well within what a Flash-tier model does reliably.
 *
 * <p>Off by default all the same. Every endpoint that reaches for a model has a
 * keyword-matching answer to fall back on and says in its response which of the
 * two the caller is looking at.
 */
@Configuration
@ConfigurationProperties(prefix = "app.gemini")
@Getter
@Setter
@NoArgsConstructor
public class GeminiProps {

    private boolean enabled = false;

    /**
     * An AI Studio API key. Blank switches the integration off however
     * {@link #enabled} is set.
     *
     * <p>Sent as {@code x-goog-api-key} rather than as the {@code ?key=} query
     * parameter the quickstarts use: a key in a URL ends up in access logs, in
     * proxy caches and in exception messages that quote the request.
     */
    private String apiKey = "";

    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * Model names move faster than this file does, and Google retires them for
     * new keys without warning — {@code gemini-2.5-flash} stood here until a
     * 404 said it was no longer available. Set {@code GEMINI_MODEL} in the
     * environment so the next retirement is a restart rather than a rebuild.
     *
     * <p>The Flash tier is the right shape for the work: one small
     * classification per draft, on a path somebody is waiting on.
     */
    private String model = "gemini-3.6-flash";

    /**
     * The answer is a handful of short objects. This exists to bound a runaway
     * rather than to size a response, and a truncated answer is a failed parse
     * rather than a partial result.
     */
    private int maxOutputTokens = 4096;

    /**
     * Zero. This is a classification with a right answer, not a piece of
     * writing, and a stable answer is also a cacheable one.
     */
    private double temperature = 0.0;

    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Covers generation, which is the slow part. Somebody pressed a button and
     * is watching a spinner, so this gives up and falls back to keyword matches
     * well before a browser would.
     */
    private Duration readTimeout = Duration.ofSeconds(30);
}
