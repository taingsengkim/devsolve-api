package kh.edu.istad.ite.devsoleapi.feature.organization.enums;

import java.util.EnumSet;
import java.util.Set;

public enum OrganizationPermission {
    VIEW_PROGRAMS,
    CREATE_PROGRAM,
    EDIT_PROGRAM,
    MANAGE_PROGRAM_STATE,
    DELETE_PROGRAM,
    VIEW_REPORTS,
    TRIAGE_REPORTS,
    MANAGE_DISCLOSURE,
    AWARD_REWARDS,
    MANAGE_RESEARCHERS,
    /**
     * Inviting teammates, changing their role or permissions, and removing
     * them. Reading the roster deliberately needs no permission at all —
     * being on a team is what entitles someone to see who else is on it.
     */
    MANAGE_MEMBERS;

    /**
     * What a role is granted when nobody says otherwise — on an invitation
     * that omits {@code permissions}, and again whenever the role changes.
     */
    public static Set<OrganizationPermission> defaultsFor(OrgRole role) {
        return switch (role) {
            case MANAGER -> EnumSet.allOf(OrganizationPermission.class);
            case MEMBER -> EnumSet.of(
                    VIEW_PROGRAMS,
                    VIEW_REPORTS,
                    TRIAGE_REPORTS,
                    MANAGE_DISCLOSURE,
                    AWARD_REWARDS
            );
            case VIEWER -> EnumSet.of(
                    VIEW_PROGRAMS,
                    VIEW_REPORTS
            );
        };
    }

    /**
     * The most a role may hold, whatever is asked for.
     *
     * <p>Role and permissions used to be independent, so a VIEWER could be
     * granted CREATE_PROGRAM and the API took it — leaving a client that gates
     * features on permissions letting somebody create programs under a badge
     * reading "Viewer". Each rank is a superset of the one below, so a
     * promotion never takes anything away.
     *
     * <p>Wider than {@link #defaultsFor} on purpose: the defaults are where a
     * member starts, this is how far they can be adjusted without changing
     * their role.
     */
    public static Set<OrganizationPermission> ceilingFor(OrgRole role) {
        return switch (role) {
            case MANAGER -> EnumSet.allOf(OrganizationPermission.class);
            case MEMBER -> EnumSet.of(
                    VIEW_PROGRAMS,
                    VIEW_REPORTS,
                    CREATE_PROGRAM,
                    EDIT_PROGRAM,
                    TRIAGE_REPORTS,
                    MANAGE_DISCLOSURE,
                    AWARD_REWARDS
            );
            case VIEWER -> EnumSet.of(
                    VIEW_PROGRAMS,
                    VIEW_REPORTS
            );
        };
    }
}
