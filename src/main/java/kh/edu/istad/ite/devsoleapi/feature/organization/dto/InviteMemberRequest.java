package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;

public record InviteMemberRequest(
        @NotBlank(message = "Invitation email is required")
        @Email(message = "Invitation email must be valid")
        String email,

        @NotNull(message = "Organization role is required")
        OrgRole role
) {
}
