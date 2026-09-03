package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAutoApprovalRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
