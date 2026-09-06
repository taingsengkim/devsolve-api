package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

/**
 * Where one researcher stands with one private program.
 *
 * <p>Two of these grant something, and they grant different things.
 * {@link #INVITED} opens the program to be read — a researcher cannot judge an
 * invitation to a programme whose scope and rules they are not allowed to see.
 * Only {@link #ACCEPTED} opens it to be reported to, because accepting is where
 * the researcher takes on the terms.
 */
public enum ProgramInvitationStatus {

    /** Asked, not yet answered. May read the program; may not report to it. */
    INVITED,

    /** Answered yes. Full access to the program, reporting included. */
    ACCEPTED,

    /**
     * Answered no. Keeps the row rather than deleting it, so the company sees
     * that they were asked and said no instead of an empty space that looks
     * like the invitation was never sent. Re-inviting reuses the row.
     */
    DECLINED,

    /** Withdrawn by the company. Access ends whichever way it was answered. */
    REVOKED
}
