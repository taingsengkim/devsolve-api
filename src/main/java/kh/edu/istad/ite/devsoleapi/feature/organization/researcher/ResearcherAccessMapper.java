package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherAccessResponse;
import org.springframework.stereotype.Component;

@Component
public class ResearcherAccessMapper {

    public ResearcherAccessResponse toResponse(
            OrganizationResearcher access
    ) {
        return new ResearcherAccessResponse(
                access.getId(),
                access.getOrganization().getId(),
                access.getOrganization().getName(),
                access.getResearcher().getId(),
                access.getResearcher().getFullName(),
                access.getResearcher().getEmail(),
                access.getStatus(),
                access.isApproved(),
                access.getMotivation(),
                access.getReviewNote(),
                access.getReviewedBy() == null
                        ? null
                        : access.getReviewedBy().getId(),
                access.getRequestedAt(),
                access.getReviewedAt(),
                access.getCreatedAt(),
                access.getUpdatedAt()
        );
    }
}
