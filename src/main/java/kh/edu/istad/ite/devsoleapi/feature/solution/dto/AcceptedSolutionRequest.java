package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptedSolutionRequest(
        @NotNull(message = "Solution ID is required")
        UUID solutionId
) {
}
