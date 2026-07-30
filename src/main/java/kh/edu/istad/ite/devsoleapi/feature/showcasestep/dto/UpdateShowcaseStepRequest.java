package kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto;

import jakarta.validation.constraints.Size;

public record UpdateShowcaseStepRequest(
        Integer stepNumber,

        @Size(max = 255)
        String title,

        String description,

        String codeSnippet,

        String imageUrl,

        String diagramUrl
) {
}
