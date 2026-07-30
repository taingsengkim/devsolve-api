package kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateShowcaseStepRequest(

        @NotNull
        @Positive
        Integer stepNumber,

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        String description,

        String codeSnippet,

        @Size(max = 500)
        String imageUrl,

        @Size(max = 500)
        String diagramUrl
) {}
