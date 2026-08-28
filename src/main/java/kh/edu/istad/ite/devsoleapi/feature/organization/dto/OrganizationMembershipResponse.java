package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * One organization the caller belongs to, and what they may do there.
 *
 * <p>Answers the question a client has on every sign-in — am I at a company,
 * and as what — which nothing else could. {@code GET /organizations/me} is
 * keyed on ownership, so it has never had an answer for someone who joined by
 * invitation, and the accepted membership existed only in the database.
 *
 * @param owner whether the caller owns the organization rather than being a
 *              member of it. Ownership is not modelled as a membership row and
 *              is not one of the three {@link OrgRole} values, so it is a flag
 *              here rather than a fourth role; {@code role} is null for an
 *              owner, whose permissions are every permission there is.
 * @param organizationStatus the organization's own state, which gates what the
 *              permissions below are worth: member management, programs and
 *              reports all require an ACTIVE organization, so a client that
 *              ignores this will offer actions the API then rejects.
 * @param joinedAt when the caller gained this access — accepting the
 *              invitation for a member, creating the organization for an owner.
 */
public record OrganizationMembershipResponse(
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        String organizationLogoUrl,
        OrganizationStatus organizationStatus,
        boolean owner,
        OrgRole role,
        Set<OrganizationPermission> permissions,
        LocalDateTime joinedAt
) {
}
