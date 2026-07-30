package kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateShowcaseStepRequest(
        @Positive
        Integer stepNumber,

        @Size(max = 255)
        String title,

        String description,

        String codeSnippet,

        @Size(max = 500)
        String imageUrl,

        @Size(max = 500)
        String diagramUrl
) {
}
