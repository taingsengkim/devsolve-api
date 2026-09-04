package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;

import java.util.List;

/**
 * The autosave payload. Everything is optional and only the caps are enforced —
 * notably not the minimums a real solution has (a 10-character summary, a
 * 30-character body), because a draft saved after one word would fail every one
 * of them.
 *
 * <p>The nested items are the same shapes a real solution takes, but their own
 * {@code @NotBlank} rules are deliberately not cascaded here: a half-typed
 * verification step is exactly what autosave exists to keep.
 */
public record SaveSolutionDraftRequest(

        @Size(max = 250, message = "Summary must be at most 250 characters")
        String summary,

        @Size(max = 30_000, message = "Solution body must be at most 30,000 characters")
        String bodyMarkdown,

        ApproachType approachType,

        @Size(max = 20, message = "A solution can contain at most 20 verification steps")
        List<VerificationStepRequest> verificationSteps,

        @Size(max = 20, message = "A solution can contain at most 20 tested technologies")
        List<TestedWithRequest> testedWith,

        @Size(max = 5_000, message = "Tradeoffs cannot exceed 5,000 characters")
        String tradeoffs,

        @Size(max = 10, message = "A solution can contain at most 10 resources")
        List<SolutionResourceRequest> resources
) {
}
