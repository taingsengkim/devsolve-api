package kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.invitation.ProgramInvitationStatus;

import java.util.List;
import java.util.UUID;

/**
 * What removing one researcher from one company's programs actually took away.
 *
 * <p>Names the programs rather than returning a bare count. This is reached
 * from a security incident — a file a scanner called dangerous — and an
 * administrator acting on one is entitled to see exactly what their click did,
 * after the fact and in the notification the researcher receives.
 *
 * @param revoked  how many invitations were withdrawn. Counts only the ones
 *                 that granted something: an invitation the researcher had
 *                 already declined is left alone, because there was no access
 *                 to remove and counting it would overstate what happened
 */
public record ProgramAccessRevocationResponse(
        UUID organizationId,

        UUID researcherId,

        int revoked,

        List<RevokedProgram> programs
) {

    /**
     * @param previousStatus what the researcher held before this. The
     *                       difference between withdrawing an unanswered
     *                       invitation and cutting off somebody who was already
     *                       working the program
     */
    public record RevokedProgram(
            UUID programId,
            String programName,
            ProgramInvitationStatus previousStatus
    ) {
    }
}
