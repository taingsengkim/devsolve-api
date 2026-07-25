package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;

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

    void removeMember(UUID targetUserId);

    MemberResponse acceptInvitation(String token);

    OrganizationResponse approve(UUID id);

    OrganizationResponse reject(UUID id);
}
