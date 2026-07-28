package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

public record ProblemModerationRequest(
        @NotNull(message = "Moderation status is required")
        ProblemStatus status
) {
}
