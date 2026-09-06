package kh.edu.istad.ite.devsoleapi.feature.program.enums;

import jakarta.persistence.EnumeratedValue;

public enum Visibility {
    PUBLIC("public"),
    PRIVATE("private"),
    INVITE_ONLY("invite_only");

    @EnumeratedValue
    private final String databaseValue;

    Visibility(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * Whether reaching this program means being on its guest list.
     *
     * <p>Asked here rather than compared against one value at each call site.
     * Both non-public visibilities are invitation-gated, and the version of
     * this that tested {@code == PRIVATE} in three places left
     * {@link #INVITE_ONLY} out of all three — invisible to the very
     * researchers invited to it on the read paths, and on the submission path
     * falling through to the company's general clearance, which is the check a
     * private program exists to not use.
     */
    public boolean isInvitationGated() {
        return this != PUBLIC;
    }
}
