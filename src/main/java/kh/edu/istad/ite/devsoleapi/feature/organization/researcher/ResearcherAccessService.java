package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.InviteResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReportingEligibilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RequestResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherAccessResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReviewResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The gate between a researcher and a company's programs. Approval is held per
 * company, not per program: one clearance covers every program that company
 * runs.
 */
public interface ResearcherAccessService {

    ResearcherAccessResponse request(
            UUID organizationId,
            RequestResearcherAccessRequest request
    );

    ResearcherAccessResponse invite(
            UUID organizationId,
            InviteResearcherRequest request
    );

    ResearcherAccessResponse review(
            UUID organizationId,
            UUID researcherId,
            ReviewResearcherAccessRequest request
    );

    Page<ResearcherAccessResponse> findForOrganization(
            UUID organizationId,
            ResearcherAccessStatus status,
            Pageable pageable
    );

    Page<ResearcherAccessResponse> findMine(
            ResearcherAccessStatus status,
            Pageable pageable
    );

    ResearcherAccessResponse findMineForOrganization(UUID organizationId);

    ReportingEligibilityResponse checkProgramEligibility(UUID programId);

    /**
     * The submission gate. Throws 403 unless the researcher holds an approved
     * standing with the company.
     */
    void requireApprovedReporter(UUID organizationId, UUID researcherId);
}
