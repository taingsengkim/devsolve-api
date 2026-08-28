package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        // only goes stale.
        @Schema(
                nullable = true,
                description = "Omit to grant the role's defaults, which "
                        + "GET /api/v1/organizations/roles publishes. Anything "
                        + "outside the role's allowed set is refused with 422."
        )
        Set<OrganizationPermission> permissions
) {
    public InviteMemberRequest(String email, OrgRole role) {
        this(email, role, null);
    }
}
