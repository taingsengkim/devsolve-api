package kh.edu.istad.ite.devsoleapi.feature.reports;

import java.util.UUID;

/**
 * @param reporterId     who filed the report, so a public comment can reach
 *                       them
 * @param organizationId the organization running the program, so the people
 *                       who can act on the report can be told about a comment
 *                       on it
 */
public record ReportDiscussionAccess(
        boolean canViewInternal,
        boolean canComment,
        boolean canCreateInternal,
        UUID reporterId,
        UUID organizationId
) {
}
