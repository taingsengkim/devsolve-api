package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.InviteToProgramRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramAccessRevocationResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramInvitationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ProgramInvitationService {

    /** Asks one researcher onto one program. Re-asking reuses the row. */
    ProgramInvitationResponse invite(
            UUID programId,
            InviteToProgramRequest request
    );

    /** The company's guest list for one program. */
    Page<ProgramInvitationResponse> findForProgram(
            UUID programId,
            ProgramInvitationStatus status,
            Pageable pageable
    );

    /**
     * The researcher's own invitations, and the only place a private program
     * they have been asked onto is discoverable.
     */
    Page<ProgramInvitationResponse> findMine(
            ProgramInvitationStatus status,
            Pageable pageable
    );

    /** The researcher takes on the program's terms. */
    ProgramInvitationResponse accept(UUID programId);

    /** The researcher says no. The row stays so the company can see it. */
    ProgramInvitationResponse decline(UUID programId);

    /** The company withdraws access, however the researcher answered. */
    ProgramInvitationResponse revoke(UUID programId, UUID researcherId);

    /**
     * Everything one researcher holds across one company's programs.
     *
     * <p>The preview behind "remove this researcher": a company looking at a
     * security incident knows who uploaded the file, not which of their
     * programs that person is on.
     */
    List<ProgramInvitationResponse> findForOrganizationResearcher(
            UUID organizationId,
            UUID researcherId
    );

    /**
     * Withdraws one researcher from every program a company runs, in one act.
     *
     * <p>Exists because the thing that prompts it — a researcher uploading a
     * file a scanner called malicious — is a judgement about the person, not
     * about one program. Revoking program by program means the company is
     * racing the researcher across their own estate, and the one they reach
     * last is the one that mattered.
     */
    ProgramAccessRevocationResponse revokeAllForOrganization(
            UUID organizationId,
            UUID researcherId
    );

    /**
     * Whether this person may read this program at all.
     *
     * <p>True for every public program, and for a private one only where the
     * caller holds an invitation they have not declined. Company staff are not
     * considered here — they reach their own programs through the management
     * paths, which check organization permissions instead.
     */
    boolean canView(Program program, UUID userId);

    /**
     * The submission gate for a private program. Throws 403 unless the
     * researcher has accepted their invitation.
     *
     * <p>Being invited is not enough. Accepting is where a researcher takes on
     * the program's terms, and a private program's terms are the reason it is
     * private.
     */
    void requireAcceptedMember(Program program, UUID researcherId);
}
