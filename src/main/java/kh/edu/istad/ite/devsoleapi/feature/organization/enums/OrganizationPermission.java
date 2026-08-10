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
    AWARD_REWARDS;

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
