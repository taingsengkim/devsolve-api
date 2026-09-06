package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherRemovalScope;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramAccessRevocationResponse;

/**
 * Everything one removal took away, both halves reported separately.
 *
 * <p>Two halves because there are two records: a standing with the company,
 * which governs its public programs, and a guest list per private program.
 * Rolling them into one number would hide the distinction that matters most
 * afterwards — whether this person can still submit to the company at all.
 *
 * @param privatePrograms            which private programs they were on, and
 *                                   what they held on each
 * @param organizationAccessRevoked  false under
 *                                   {@link ResearcherRemovalScope#PRIVATE_PROGRAMS},
 *                                   and also under
 *                                   {@link ResearcherRemovalScope#ENTIRE_COMPANY}
 *                                   when there was nothing to revoke — a
 *                                   researcher who never held clearance with
 *                                   this company could not submit to its public
 *                                   programs in the first place
 * @param previousOrganizationStatus what that standing was before, or null when
 *                                   nothing changed
 */
public record ResearcherRemovalResponse(
        ResearcherRemovalScope scope,

        ProgramAccessRevocationResponse privatePrograms,

        boolean organizationAccessRevoked,

        ResearcherAccessStatus previousOrganizationStatus
) {
}
