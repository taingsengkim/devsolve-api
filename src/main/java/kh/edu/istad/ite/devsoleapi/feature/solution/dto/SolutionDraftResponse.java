package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An answer in progress. Every field but the identifiers may be null — that is
 * what unfinished means.
 */
public record SolutionDraftResponse(
        UUID id,
        UUID authorId,
        UUID problemId,
        String summary,
        String bodyMarkdown,
        ApproachType approachType,
        List<VerificationStepRequest> verificationSteps,
        List<TestedWithRequest> testedWith,
        String tradeoffs,
        List<SolutionResourceRequest> resources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
