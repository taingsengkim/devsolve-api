package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One researcher's standing with one private program.
 *
 * <p>Deliberately not the same thing as
 * {@code OrganizationResearcher}, which is a standing with a whole company and
 * covers every program it runs. A private program is private from the company's
 * own approved researchers too — that is what makes it private — so its guest
 * list has to be held per program.
 *
 * <p>At most one row per pair, reused as the relationship changes rather than
 * replaced. A researcher who declined and is asked again moves this row back to
 * {@link ProgramInvitationStatus#INVITED}, so the company never accumulates a
 * pile of rows about one person.
 *
 * <p>Kept on programs of any visibility. An organization builds its guest list
 * before flipping a program private, and refusing invitations until then would
 * force them to publish an empty private program and invite into it afterwards.
 * The list is simply not consulted while a program is public.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "program_invitations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_program_invitations_program_user",
                columnNames = {"program_id", "user_id"}
        )
)
public class ProgramInvitation extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile researcher;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProgramInvitationStatus status = ProgramInvitationStatus.INVITED;

    /** What the company said when they asked. Null when they said nothing. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    private UserProfile invitedBy;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    /** When the researcher answered, or when the company withdrew it. */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    /**
     * Passes this relationship has been through. Notifications key on it, so
     * being invited again after declining is not swallowed as a duplicate of
     * the first invitation.
     */
    @Column(name = "revision", nullable = false)
    private int revision = 0;

    public ProgramInvitation(Program program, UserProfile researcher) {
        this.program = program;
        this.researcher = researcher;
    }

    public void invite(UserProfile invitedBy, String note) {
        this.status = ProgramInvitationStatus.INVITED;
        this.invitedBy = invitedBy;
        this.note = note;
        this.invitedAt = LocalDateTime.now();
        this.respondedAt = null;
        this.revision++;
    }

    public void accept() {
        settle(ProgramInvitationStatus.ACCEPTED);
    }

    public void decline() {
        settle(ProgramInvitationStatus.DECLINED);
    }

    public void revoke() {
        settle(ProgramInvitationStatus.REVOKED);
    }

    private void settle(ProgramInvitationStatus outcome) {
        this.status = outcome;
        this.respondedAt = LocalDateTime.now();
        this.revision++;
    }

    /** May read the program. Deciding on an invitation requires reading it. */
    public boolean canView() {
        return status == ProgramInvitationStatus.INVITED
                || status == ProgramInvitationStatus.ACCEPTED;
    }

    /** May submit reports. Only after taking on the terms. */
    public boolean canReport() {
        return status == ProgramInvitationStatus.ACCEPTED;
    }
}
