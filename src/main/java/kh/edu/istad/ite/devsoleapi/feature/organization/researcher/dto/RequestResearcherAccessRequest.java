package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestResearcherAccessRequest(
        @NotBlank(message = "Tell the company who you are and why you want access")
        @Size(
                min = 20,
                max = 2000,
                message = "Your introduction must be between 20 and 2000 characters"
        )
        String motivation
) {
}
