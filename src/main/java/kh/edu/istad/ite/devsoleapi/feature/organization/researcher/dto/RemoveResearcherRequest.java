package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherRemovalScope;

/**
 * @param scope how far to remove them. Required rather than defaulted: the two
 *              scopes differ by whether the researcher can still report to the
 *              company at all, and a default would decide that silently
 * @param note  why. Reaches the researcher in the notification, and it is the
 *              only thing that distinguishes being cut off for uploading
 *              malware from being cut off with no explanation at all
 */
public record RemoveResearcherRequest(

        @NotNull(message = "Removal scope is required")
        ResearcherRemovalScope scope,

        @Size(max = 2000, message = "Note must not exceed 2000 characters")
        String note
) {
}
