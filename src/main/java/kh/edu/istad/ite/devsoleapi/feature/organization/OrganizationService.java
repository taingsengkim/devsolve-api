package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.*;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse register(OrganizationRequest request);

    OrganizationResponse me();

    OrganizationResponse updateMe(OrganizationUpdateRequest request);

    void deleteMe();

    OrganizationResponse getById(UUID id);

    OrganizationResponse getBySlug(String slug);

    List<MemberResponse> getMyMembers();

    InvitationResponse inviteMember(InviteMemberRequest request);

    MemberResponse updateMemberRole(
            UUID targetUserId,
            UpdateMemberRoleRequest request
    );

    MemberResponse updateMemberPermissions(
            UUID targetUserId,
            UpdateMemberPermissionsRequest request
    );

    void removeMember(UUID targetUserId);

    MemberResponse acceptInvitation(String token);

    OrganizationResponse approve(UUID id);

    OrganizationResponse reject(UUID id);

    Page<OrganizationReviewSummaryResponse> getPendingOrganizations(
            int pageNumber,
            int pageSize
    );

    OrganizationReviewResponse getForReview(UUID id);
}
