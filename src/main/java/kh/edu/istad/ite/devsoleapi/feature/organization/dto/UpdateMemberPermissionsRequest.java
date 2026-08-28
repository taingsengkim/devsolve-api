package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;

import java.util.Set;

public record UpdateMemberPermissionsRequest(
        // Deliberately uncapped. A Set of an enum cannot hold more than the
        // enum has values, so a numeric limit adds nothing and silently
        // becomes wrong the moment a permission is added — which is exactly
        // what a cap of 10 did when MANAGE_MEMBERS made it eleven, rejecting
        // the one request that grants a manager everything.
        @NotNull(message = "Permissions are required")
        Set<OrganizationPermission> permissions
) {
}
