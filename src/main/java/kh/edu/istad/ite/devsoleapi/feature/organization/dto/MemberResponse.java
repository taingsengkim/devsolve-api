package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        /** The handle their profile is reachable at, for linking out. */
        String username,
        String name,
        String email,

        @Schema(
                nullable = true,
                description = "Null for the owner, who holds every permission "
                        + "without holding a role. Constrains permissions: a "
                        + "member may not be granted more than the role allows."
        )
        OrgRole role,

        Set<OrganizationPermission> permissions,

        @Schema(
                description = "ACTIVE for somebody on the team, SUSPENDED for "
                        + "an invitation not yet accepted. REMOVED members are "
                        + "not listed at all."
        )
        MembershipStatus status,

        boolean invitationPending,
        boolean self,
        boolean owner,
        LocalDateTime joinedAt
) {
}
