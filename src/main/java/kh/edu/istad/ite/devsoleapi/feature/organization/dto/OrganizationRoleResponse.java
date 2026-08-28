package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;

import java.util.Set;

/**
 * What one organization role means, in the API's own words.
 *
 * <p>Published because a client that gates features on permissions has to
 * agree with the server about which ones a role carries, and a table copied
 * into a client drifts from this one without either side noticing.
 *
 * @param defaultPermissions what an invitation that omits {@code permissions}
 *                           grants, and what a role change resets a member to.
 * @param allowedPermissions the most this role may hold. Anything outside it is
 *                           refused with 422.
 */
@Schema(description = "The permissions an organization role carries")
public record OrganizationRoleResponse(
        OrgRole role,
        Set<OrganizationPermission> defaultPermissions,
        Set<OrganizationPermission> allowedPermissions
) {
}
