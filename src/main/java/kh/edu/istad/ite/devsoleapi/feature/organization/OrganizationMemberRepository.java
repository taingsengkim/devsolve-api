package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    /**
     * Invitations issued to one user and not yet answered.
     *
     * <p>Fetches the organization and the inviter along with the membership:
     * every row of the response names both, and lazily walking to them would
     * turn one query into three per invitation.
     *
     * <p>Expiry is deliberately not in the {@code where} clause. It is a
     * function of {@code updatedAt} plus a window that
     * {@link OrganizationMember#INVITATION_VALID_DAYS} owns, and restating
     * that arithmetic in JPQL is how the two get to disagree.
     */
    @Query("""
            select member
            from OrganizationMember member
            join fetch member.organization
            join fetch member.user
            left join fetch member.invitedBy
            where member.user.id = :userId
              and member.status = :status
              and member.invitationToken is not null
            """)
    List<OrganizationMember> findPendingInvitations(
            @Param("userId") UUID userId,
            @Param("status") MembershipStatus status
    );
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

    Optional<OrganizationMember> findByUserId(UUID userId);
}
