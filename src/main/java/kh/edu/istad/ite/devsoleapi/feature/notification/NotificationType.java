package kh.edu.istad.ite.devsoleapi.feature.notification;

import jakarta.persistence.EnumeratedValue;

public enum NotificationType {
    COMMENT("comment"),
    REPORT("report"),
    PROGRAM("program"),
    SOLUTION("solution"),
    PROBLEM("problem"),
    KYC("kyc"),
    ORGANIZATION("organization"),
    INVITATION("invitation"),
    DISPUTE("dispute"),
    RECOGNITION("recognition"),
    SHOWCASE("showcase"),
    // Notifiable is a person rather than a piece of content — a new follower,
    // where the id to open is the follower's profile.
    USER("user"),
    REWARD("reward"),
    // A refused upload, a blocked link — the platform stopping something
    // rather than moving it along. The id to open is whatever the content was
    // being attached to.
    SECURITY("security");

    @EnumeratedValue
    private final String databaseValue;

    NotificationType(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /**
     * Whether this kind of notification is emailed to someone who has never
     * touched their settings.
     *
     * <p>The line is whether missing it costs the person something. A report
     * moving state, a reward, a dispute, an organization decision — those are
     * the platform acting on their work, and a researcher who closed the tab
     * has no other way to learn about them. Comments, follows and recognitions
     * are pleasant, not urgent, and mailing all of them by default is how a
     * product teaches people to filter its mail.
     *
     * <p>{@code INVITATION} is off here because it is not unmailed — it has
     * its own message with an accept button, sent by
     * OrganizationInvitationMailer. Turning it on would send two emails for
     * one invitation.
     */
    public boolean emailedByDefault() {
        return switch (this) {
            // SECURITY is mailed because it is the platform reporting that it
            // refused something dangerous. Nobody opts into hearing that late.
            case REPORT, REWARD, DISPUTE, ORGANIZATION, KYC, SECURITY -> true;
            case COMMENT, PROGRAM, SOLUTION, PROBLEM,
                 RECOGNITION, SHOWCASE, USER, INVITATION -> false;
        };
    }
}
