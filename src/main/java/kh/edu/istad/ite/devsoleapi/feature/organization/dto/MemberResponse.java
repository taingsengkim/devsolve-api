package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder
public record MemberResponse(
        UUID userId,
        String name,
        String email,
        OrgRole role,
        Set<OrganizationPermission> permissions,
        MembershipStatus status,
        boolean invitationPending,
        LocalDateTime joinedAt
) {
}
