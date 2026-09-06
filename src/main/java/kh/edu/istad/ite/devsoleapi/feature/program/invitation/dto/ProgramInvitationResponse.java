package kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.invitation.ProgramInvitationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One invitation, read from either end.
 *
 * <p>Carries both the program and the researcher, because the two lists it
 * serves need opposite halves: a company reading its guest list knows the
 * program and needs the people, and a researcher reading their invitations
 * knows themselves and needs the programs. One shape rather than two that
 * drift, and the half you already know costs nothing to send.
 *
 * @param programName  the private program's name. This response is the only
 *                     place an invited researcher can learn it — a private
 *                     program appears in no public listing
 * @param respondedAt  when the researcher answered, or when the company
 *                     withdrew it
 */
public record ProgramInvitationResponse(
        UUID id,

        UUID programId,

        String programHandle,

        String programName,

        UUID organizationId,

        ProgramInvitationStatus status,

        UUID researcherId,

        String researcherName,

        String researcherAvatarUrl,

        UUID invitedBy,

        String note,

        LocalDateTime invitedAt,

        LocalDateTime respondedAt,

        LocalDateTime createdAt,

        LocalDateTime updatedAt
) {
}
