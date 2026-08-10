package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse register(OrganizationRequest request);

    OrganizationResponse me();

    OrganizationVerificationResponse getVerificationStatus();

    void resendVerificationEmail();

    OrganizationResponse updateMe(OrganizationUpdateRequest request);

    OrganizationResponse uploadLogo(MultipartFile file);

    OrganizationResponse removeLogo();

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

    OrganizationResponse reject(UUID id, RejectOrganizationRequest request);

    OrganizationResponse resubmit();

    Page<OrganizationReviewSummaryResponse> getPendingOrganizations(
            int pageNumber,
            int pageSize
    );

    OrganizationReviewResponse getForReview(UUID id);

    Page<OrganizationReviewHistoryResponse> getReviewHistory(
            UUID id,
            int pageNumber,
            int pageSize
    );
}
