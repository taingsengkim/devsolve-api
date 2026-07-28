package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemContentSafetyTest {

    private final ProblemContentSafety contentSafety =
            new ProblemContentSafety();

    @Test
    void rejectsDangerousHtml() {
        assertThrows(
                ResponseStatusException.class,
                () -> contentSafety.normalizeText(
                        "<script>alert('xss')</script>"
                )
        );
    }

    @Test
    void warnsAboutLikelySecrets() {
        assertFalse(contentSafety.warnings(
                "Configuration issue",
                "api_key=super-secret-value"
        ).isEmpty());
    }
}
