package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationMembershipResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.PendingInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationMapper {

    private final WebsiteUrlService websiteUrlService;

    public Organization toOrganization(
            OrganizationRequest request,
            UserProfile owner,
            String slug
    ) {
        Organization organization = new Organization();
        organization.setName(request.companyName().trim());
        organization.setSlug(slug);
        organization.setWebsiteUrl(
                websiteUrlService.normalize(request.companyWebsite())
        );
        organization.setOwnerJobTitle(request.jobTitle().trim());
        organization.setCompanySize(request.companySize());
        organization.setCountry(request.country().trim());
        organization.setJoiningReason(request.joiningReason().trim());
        organization.setIndustry(request.industry());
        organization.setOwner(owner);
        return organization;
    }

    public void updateOrganization(
            OrganizationUpdateRequest request,
            Organization organization
    ) {
        if (request.name() != null) {
            organization.setName(request.name().trim());
        }
        if (request.logoUrl() != null) {
            organization.setLogoUrl(trimToNull(request.logoUrl()));
        }
        if (request.coverImageUrl() != null) {
            organization.setCoverImageUrl(
                    trimToNull(request.coverImageUrl())
            );
        }
        if (request.description() != null) {
            organization.setDescription(trimToNull(request.description()));
        }
        if (request.industry() != null) {
            organization.setIndustry(request.industry());
        }
    }

    public OrganizationResponse toOrganizationResponse(Organization organization) {
        return toOrganizationResponse(organization, null);
    }

    public OrganizationResponse toOrganizationResponse(
            Organization organization,
            OrganizationStatsResponse stats
    ) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getOwner().getId(),
                organization.getName(),
                organization.getSlug(),
                websiteUrlService.extractDomain(organization.getWebsiteUrl()),
                organization.getWebsiteUrl(),
                organization.getLogoUrl(),
                organization.getCoverImageUrl(),
                organization.getDescription(),
                organization.getIndustry(),
                organization.getCompanySize(),
                organization.getCountry(),
                organization.getStatus(),
                organization.getSubmissionVersion(),
                organization.getRejectionReason(),
                organization.getReviewedAt(),
                organization.getVerifiedAt(),
                organization.getCreatedAt(),
                organization.getUpdatedAt(),
                stats
        );
    }

    public OrganizationReviewSummaryResponse toReviewSummary(
            Organization organization
    ) {
        UserProfile owner = organization.getOwner();
        return new OrganizationReviewSummaryResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getWebsiteUrl(),
                organization.getIndustry(),
                organization.getCompanySize(),
                organization.getCountry(),
                organization.getStatus(),
                owner.getId(),
                owner.getFullName(),
                owner.getEmail(),
                owner.getPhone(),
                organization.getSubmissionVersion(),
                organization.getCreatedAt()
        );
    }

    public OrganizationReviewResponse toReviewResponse(
            Organization organization,
            boolean emailVerified
    ) {
        UserProfile owner = organization.getOwner();
        return new OrganizationReviewResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                websiteUrlService.extractDomain(
                        organization.getWebsiteUrl()
                ),
                organization.getWebsiteUrl(),
                organization.getLogoUrl(),
                organization.getDescription(),
                organization.getIndustry(),
                organization.getCompanySize(),
                organization.getCountry(),
                organization.getStatus(),
                owner.getId(),
                owner.getFullName(),
                owner.getEmail(),
                organization.getOwnerJobTitle(),
                organization.getJoiningReason(),
                emailVerified,
                organization.getSubmissionVersion(),
                organization.getReviewedBy(),
                organization.getReviewedAt(),
                organization.getRejectionReason(),
                organization.getVerifiedAt(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }

    public OrganizationReviewHistoryResponse toReviewHistoryResponse(
            OrganizationReviewHistory history
    ) {
        return new OrganizationReviewHistoryResponse(
                history.getId(),
                history.getSubmissionVersion(),
                history.getDecision(),
                history.getReviewerId(),
                history.getReason(),
                history.getReviewedAt()
        );
    }

    public MemberResponse toMemberResponse(
            OrganizationMember member,
            UUID callerId
    ) {
        UserProfile user = member.getUser();
        return MemberResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .name(user.getFullName())
                .email(user.getEmail())
                .role(member.getRole())
                .permissions(Set.copyOf(member.getPermissions()))
                .status(member.getStatus())
                .invitationPending(member.isInvitationPending())
                .self(user.getId().equals(callerId))
                .owner(false)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    /**
     * The owner, in the shape a roster row takes.
     *
     * <p>Ownership is not a membership row — {@code inviteMember} refuses to
     * invite the owner — so a roster built from {@code organization_members}
     * alone omits the one person who is always on the team, and every row it
     * does return is somebody else. That is what left a client unable to tag
     * either "You" or "Owner".
     */
    public MemberResponse toOwnerMemberResponse(
            Organization organization,
            UUID callerId
    ) {
        UserProfile owner = organization.getOwner();
        return MemberResponse.builder()
                .userId(owner.getId())
                .username(owner.getUsername())
                .name(owner.getFullName())
                .email(owner.getEmail())
                .role(null)
                .permissions(Set.copyOf(
                        EnumSet.allOf(OrganizationPermission.class)
                ))
                .status(MembershipStatus.ACTIVE)
                .invitationPending(false)
                .self(owner.getId().equals(callerId))
                .owner(true)
                .joinedAt(organization.getCreatedAt())
                .build();
    }

    public OrganizationMembershipResponse toMembership(
            OrganizationMember member
    ) {
        Organization organization = member.getOrganization();
        return new OrganizationMembershipResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getLogoUrl(),
                organization.getStatus(),
                false,
                member.getRole(),
                Set.copyOf(member.getPermissions()),
                member.getJoinedAt()
        );
    }

    /**
     * The owner's own organization, in the shape a membership takes.
     *
     * <p>Ownership is not a row in {@code organization_members}, so a caller
     * listing what they belong to would otherwise see their own company only if
     * somebody had invited them to it. Every permission is granted because
     * {@code OrganizationAuthorizationService} short-circuits on ownership
     * before it ever reads a permission set.
     */
    public OrganizationMembershipResponse toOwnerMembership(
            Organization organization
    ) {
        return new OrganizationMembershipResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getLogoUrl(),
                organization.getStatus(),
                true,
                null,
                Set.copyOf(EnumSet.allOf(OrganizationPermission.class)),
                organization.getCreatedAt()
        );
    }

    public PendingInvitationResponse toPendingInvitation(
            OrganizationMember member
    ) {
        Organization organization = member.getOrganization();
        UserProfile invitedBy = member.getInvitedBy();
        return new PendingInvitationResponse(
                member.getInvitationToken(),
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getLogoUrl(),
                member.getRole(),
                invitedBy == null ? null : invitedBy.getFullName(),
                member.getUpdatedAt(),
                member.invitationExpiresAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
