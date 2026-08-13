package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ProgramSpecification {

    private ProgramSpecification() {
    }

    public static Specification<Program> organizationPrograms(
            UUID organizationId
    ) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(
                        root.get("organizationId"),
                        organizationId
                ),
                criteriaBuilder.isNull(root.get("deletedAt"))
        );
    }

    /**
     * The organization's recycle bin. Deleted programs are hidden from every
     * other listing, so this is the only way an owner can find the id they
     * need to restore.
     */
    public static Specification<Program> deletedOrganizationPrograms(
            UUID organizationId
    ) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(
                        root.get("organizationId"),
                        organizationId
                ),
                criteriaBuilder.isNotNull(root.get("deletedAt"))
        );
    }

    public static Specification<Program> programsForReview(
            SubmissionState submissionState
    ) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(
                        root.get("submissionState"),
                        submissionState
                ),
                criteriaBuilder.isNull(root.get("deletedAt"))
        );
    }
}
