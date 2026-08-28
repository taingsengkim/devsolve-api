package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * One person on an organization's team.
 *
 * @param self  whether this row is the caller. Without it a roster is a list
 *              of strangers: the client cannot tag its own row, and offers
 *              actions against the caller that the API then refuses.
 * @param owner whether this person registered the company. Ownership is not a
 *              membership row and not one of the {@link OrgRole} values, so it
 *              is a flag here and {@code role} is null for the owner — the
 *              same shape {@code OrganizationMembershipResponse} uses.
 */
@Builder
public record MemberResponse(
        UUID userId,
        String name,
        String email,
        OrgRole role,
        Set<OrganizationPermission> permissions,
        MembershipStatus status,
        boolean invitationPending,
        boolean self,
        boolean owner,
        LocalDateTime joinedAt
) {
}
