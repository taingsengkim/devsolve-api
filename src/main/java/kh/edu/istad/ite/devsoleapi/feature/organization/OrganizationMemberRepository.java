package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    List<OrganizationMember> findByOrganizationIdAndStatusNot(
            UUID organizationId,
            MembershipStatus status
    );

    List<OrganizationMember> findByUserIdAndStatus(
            UUID userId,
            MembershipStatus status
    );

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMember> findByInvitationToken(String invitationToken);
}
