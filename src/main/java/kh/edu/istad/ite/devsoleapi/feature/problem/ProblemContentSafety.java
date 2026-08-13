package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.content.ContentSafetyRules;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The problem feature's view of {@link ContentSafetyRules}: same rules, but
 * rejections name the problem so the author knows which field to fix.
 */
@Component
public class ProblemContentSafety {

    public String normalizeText(String value) {
        return ContentSafetyRules.normalizeText(value, "Problem content");
    }

    public List<String> warnings(String title, String description) {
        return ContentSafetyRules.warnings(title, description);
    }
}
