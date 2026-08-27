package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessDecision;

public record ReviewResearcherAccessRequest(
        @NotNull(message = "A decision is required")
        ResearcherAccessDecision decision,

        @Size(max = 2000, message = "A note cannot exceed 2000 characters")
        String note
) {
}
