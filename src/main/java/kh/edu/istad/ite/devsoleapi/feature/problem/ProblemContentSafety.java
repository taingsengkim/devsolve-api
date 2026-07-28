package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ProblemContentSafety {

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

    public String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\u0000", "").trim();
        if (DANGEROUS_HTML.matcher(normalized).find()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Problem content contains unsafe HTML"
            );
        }
        return normalized;
    }

    public List<String> warnings(String title, String description) {
        String content = (title == null ? "" : title)
                + "\n"
                + (description == null ? "" : description);
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
