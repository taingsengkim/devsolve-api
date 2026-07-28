package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProblemResponse(
        UUID id,
        UUID authorId,
        UUID categoryId,
        String title,
        String description,
        ProblemStatus status,
        Integer viewCount
) {
}
