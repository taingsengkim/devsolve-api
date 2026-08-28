package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;

import java.util.Set;

public record InviteMemberRequest(
        @NotBlank(message = "Invitation email is required")
        @Email(message = "Invitation email must be valid")
        String email,

        @NotNull(message = "Organization role is required")
        OrgRole role,

        // Uncapped for the same reason as UpdateMemberPermissionsRequest: a
        // Set of an enum is already bounded by the enum, and a number here
        // only goes stale. Null means "use the role's defaults".
        Set<OrganizationPermission> permissions
) {
    public InviteMemberRequest(String email, OrgRole role) {
        this(email, role, null);
    }
}
