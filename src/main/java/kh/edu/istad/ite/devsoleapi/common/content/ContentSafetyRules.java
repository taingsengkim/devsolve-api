package kh.edu.istad.ite.devsoleapi.common.content;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The rules for user-submitted prose, shared by every feature that stores it.
 *
 * <p>These started out inside the problem feature. Comments need exactly the
 * same treatment — they are free text, written by anybody, and rendered as
 * markup by the client — so the patterns live here and the feature-level
 * components delegate rather than each carrying their own copy that drifts.
 *
 * <p>Deliberately static rather than a bean. Two features holding a reference
 * to the same rules must not be able to disagree about them, and there is no
 * state or collaborator here worth injecting.
 */
public final class ContentSafetyRules {

    /**
     * Postgres rejects a NUL inside a text value outright, so one arriving in
     * a request body is either a probe or a broken client. Stripped rather
     * than rejected: the rest of the text is usually exactly what the author
     * meant to send.
     */
    private static final String NUL = String.valueOf((char) 0);

    private static final Pattern DANGEROUS_HTML = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed|style)\\b"
                    + "|javascript\\s*:"
                    + "|\\bon(?:error|load|click|mouseover)\\s*="
    );

    private static final List<SecretPattern> SECRET_PATTERNS = List.of(
            new SecretPattern(
                    "A private key may be present",
                    Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
            ),
            new SecretPattern(
                    "A JWT or access token may be present",
                    Pattern.compile(
                            "\\beyJ[A-Za-z0-9_-]{10,}\\."
                                    + "[A-Za-z0-9_-]{10,}\\."
                                    + "[A-Za-z0-9_-]{10,}\\b"
                    )
            ),
            new SecretPattern(
                    "A password or secret assignment may be present",
                    Pattern.compile(
                            "(?i)\\b(password|passwd|secret|api[_-]?key)"
                                    + "\\s*[:=]\\s*[^\\s]{6,}"
                    )
            )
    );

    private ContentSafetyRules() {
    }

    /**
     * @param subject what to call the text in the rejection message, so the
     *                caller reads "Problem content contains unsafe HTML"
     *                rather than something generic they have to interpret.
     */
    public static String normalizeText(String value, String subject) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace(NUL, "").trim();
        if (DANGEROUS_HTML.matcher(normalized).find()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    subject + " contains unsafe HTML"
            );
        }
        if (ProfanityFilter.scan(normalized).isBlocked()) {
            // The term is not named back to the author. They know what they
            // wrote, repeating a slur in an API error helps nobody, and an
            // account probing for the list learns nothing from a refusal
            // that reads the same however it was triggered.
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    subject + " contains language that is not allowed here"
            );
        }
        return normalized;
    }

    /**
     * The milder half of the word lists: terms a moderator should see but
     * that do not justify refusing the write.
     *
     * <p>Separate from {@link #normalizeText} because the two halves are
     * acted on in different places. Blocking happens on the way in, where
     * the text is; flagging happens after the row exists, because a flag
     * needs something to point at.
     */
    public static List<String> profanity(String... parts) {
        return ProfanityFilter.scan(parts).flagged();
    }

    public static List<String> warnings(String... parts) {
        StringBuilder content = new StringBuilder();
        for (String part : parts) {
            content.append(part == null ? "" : part).append('\n');
        }
        List<String> warnings = new ArrayList<>();
        for (SecretPattern secretPattern : SECRET_PATTERNS) {
            if (secretPattern.pattern().matcher(content).find()) {
                warnings.add(secretPattern.warning());
            }
        }
        return List.copyOf(warnings);
    }

    private record SecretPattern(String warning, Pattern pattern) {
    }
}
