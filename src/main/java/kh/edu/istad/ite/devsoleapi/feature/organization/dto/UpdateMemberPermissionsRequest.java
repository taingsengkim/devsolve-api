package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;

import java.util.Set;

public record UpdateMemberPermissionsRequest(
        @NotNull(message = "Permissions are required")
        @Size(
                max = 8,
                message = "At most 8 organization permissions are allowed"
        )
        Set<OrganizationPermission> permissions
) {
}
