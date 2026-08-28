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
}
