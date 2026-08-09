package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;

import java.util.List;

public record SolutionRequest(
        @NotBlank(message = "Summary is required")
        @Size(min = 10, max = 250, message = "Summary must be between 10 and 250 characters")
        String summary,

        @NotBlank(message = "Solution body is required")
        @Size(min = 30, max = 30_000, message = "Solution body must be between 30 and 30,000 characters")
        String bodyMarkdown,

        @NotNull(message = "Approach type is required")
        ApproachType approachType,

        @Size(max = 20, message = "A solution can contain at most 20 verification steps")
        List<@Valid VerificationStepRequest> verificationSteps,

        @Size(max = 20, message = "A solution can contain at most 20 tested technologies")
        List<@Valid TestedWithRequest> testedWith,

        @Size(max = 5_000, message = "Tradeoffs cannot exceed 5,000 characters")
        String tradeoffs,

        @Size(max = 10, message = "A solution can contain at most 10 resources")
        List<@Valid SolutionResourceRequest> resources
) {
}
