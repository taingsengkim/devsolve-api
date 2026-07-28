package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProblemRequest(
        @NotNull UUID categoryId,
        @NotBlank String title,
        @NotBlank String description
) {}

