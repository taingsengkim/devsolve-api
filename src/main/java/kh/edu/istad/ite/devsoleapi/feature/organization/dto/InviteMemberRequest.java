package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;

import java.util.Set;

public record InviteMemberRequest(
        @NotBlank(message = "Invitation email is required")
        @Email(message = "Invitation email must be valid")
        String email,

        @NotNull(message = "Organization role is required")
        OrgRole role,

        @Size(
                max = 10,
                message = "At most 10 organization permissions are allowed"
        )
        Set<OrganizationPermission> permissions
) {
    public InviteMemberRequest(String email, OrgRole role) {
        this(email, role, null);
    }
}
