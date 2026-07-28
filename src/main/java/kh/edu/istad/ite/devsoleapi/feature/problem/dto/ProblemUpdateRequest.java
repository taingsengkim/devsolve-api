package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProblemUpdateRequest(
        UUID categoryId,
        String title,
        String description
) {
}
