package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

public record ProblemDetailResponse(
        UUID id,
        UUID authorId,
        UUID categoryId,
        String title,
        String description,
        ProblemStatus status,
        Integer viewCount
        // later: List<SolutionSummary> solutions
) {
}
