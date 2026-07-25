package co.istad.ite.devsoleapi.feature.showcasestep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateShowcaseStepRequest(

        @NotNull
        Integer stepNumber,

        @NotBlank
        @Size(max = 255)
        String title,

        @NotBlank
        String description,

        String codeSnippet,

        String imageUrl,

        String diagramUrl
) {}
