package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.InviteResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReportingEligibilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RequestResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherAccessResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RemoveResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherRemovalResponse;
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

    /**
     * Removes a researcher from a company, as far as the company asks.
     *
     * <p>The action offered beside a security incident. A researcher whose
     * upload a scanner called malicious is a judgement about the person, and
     * acting on it means reaching both records that let them submit: their
     * standing with the company, which governs its public programs, and their
     * invitations to its private ones. Doing it in two calls leaves a window
     * between them, and leaves whichever the operator forgets standing.
     *
     * <p>Forgiving where {@link #review} is strict. That path refuses to revoke
     * a researcher who was never approved, which is correct for an
     * administrator working a queue and wrong here: the company is not
     * adjusting a decision, it is closing everything this person holds,
     * whatever state each part happens to be in.
     */
    ResearcherRemovalResponse removeResearcher(
            UUID organizationId,
            UUID researcherId,
            RemoveResearcherRequest request
    );
}
