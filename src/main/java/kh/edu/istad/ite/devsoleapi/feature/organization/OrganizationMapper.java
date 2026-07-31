package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

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
        if (request.description() != null) {
            organization.setDescription(trimToNull(request.description()));
        }
        if (request.industry() != null) {
            organization.setIndustry(request.industry());
        }
    }

    public OrganizationResponse toOrganizationResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getOwner().getId(),
                organization.getName(),
                organization.getSlug(),
                websiteUrlService.extractDomain(organization.getWebsiteUrl()),
                organization.getWebsiteUrl(),
                organization.getLogoUrl(),
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
                organization.getUpdatedAt()
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

    public MemberResponse toMemberResponse(OrganizationMember member) {
        return MemberResponse.builder()
                .userId(member.getUser().getId())
                .name(member.getUser().getFullName())
                .email(member.getUser().getEmail())
                .role(member.getRole())
                .permissions(Set.copyOf(member.getPermissions()))
                .status(member.getStatus())
                .invitationPending(member.isInvitationPending())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
