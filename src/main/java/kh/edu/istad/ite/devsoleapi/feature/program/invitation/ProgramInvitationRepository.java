package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProgramInvitationRepository
        extends JpaRepository<ProgramInvitation, UUID> {

    /**
     * The access check, and the busiest query here — every read of a private
     * program runs it.
     */
    Optional<ProgramInvitation> findByProgram_IdAndResearcher_Id(
            UUID programId,
            UUID researcherId
    );

    /**
     * The company's guest list.
     *
     * <p>The researcher is fetched rather than left lazy: every row of the
     * response names them, so without this a page of twenty costs twenty more
     * selects. The program is already known — the caller looked it up to check
     * permissions — so it is left alone.
     */
    @Query(
            value = """
                    select invitation
                    from ProgramInvitation invitation
                    join fetch invitation.researcher
                    where invitation.program.id = :programId
                      and (:status is null or invitation.status = :status)
                    """,
            countQuery = """
                    select count(invitation)
                    from ProgramInvitation invitation
                    where invitation.program.id = :programId
                      and (:status is null or invitation.status = :status)
                    """
    )
    Page<ProgramInvitation> findForProgram(
            @Param("programId") UUID programId,
            @Param("status") ProgramInvitationStatus status,
            Pageable pageable
    );

    /**
     * A researcher's own invitations.
     *
     * <p>Fetches the program, which every row names and which is the whole
     * point of the list — a private program is not in any public listing, so
     * this is the only place a researcher can find it.
     *
     * <p>Deleted programs are excluded. An invitation to something that no
     * longer exists is not an invitation.
     */
    @Query(
            value = """
                    select invitation
                    from ProgramInvitation invitation
                    join fetch invitation.program program
                    where invitation.researcher.id = :researcherId
                      and program.deletedAt is null
                      and (:status is null or invitation.status = :status)
                    """,
            countQuery = """
                    select count(invitation)
                    from ProgramInvitation invitation
                    where invitation.researcher.id = :researcherId
                      and invitation.program.deletedAt is null
                      and (:status is null or invitation.status = :status)
                    """
    )
    Page<ProgramInvitation> findForResearcher(
            @Param("researcherId") UUID researcherId,
            @Param("status") ProgramInvitationStatus status,
            Pageable pageable
    );

    long countByProgram_IdAndStatus(
            UUID programId,
            ProgramInvitationStatus status
    );
}
