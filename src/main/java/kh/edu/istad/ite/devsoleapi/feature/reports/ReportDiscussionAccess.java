package kh.edu.istad.ite.devsoleapi.feature.reports;

public record ReportDiscussionAccess(
        boolean canViewInternal,
        boolean canComment,
        boolean canCreateInternal
) {
}
