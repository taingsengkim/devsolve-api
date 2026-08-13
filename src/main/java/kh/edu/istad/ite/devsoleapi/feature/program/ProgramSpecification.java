package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.persistence.criteria.Predicate;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProgramSpecification {

    private ProgramSpecification() {
    }

    public static Specification<Program> publicPrograms(
            UUID organizationId,
            EngagementType engagementType,
            Boolean offersBounties
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            predicates.add(criteriaBuilder.equal(
                    root.get("state"),
                    ProgramState.ACTIVE
            ));
            predicates.add(criteriaBuilder.equal(
                    root.get("submissionState"),
                    SubmissionState.APPROVED
            ));
            predicates.add(criteriaBuilder.equal(
                    root.get("visibility"),
                    Visibility.PUBLIC
            ));

            if (organizationId != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("organizationId"),
                        organizationId
                ));
            }
            if (engagementType != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("engagementType"),
                        engagementType
                ));
            }
            if (offersBounties != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("offersBounties"),
                        offersBounties
                ));
            }

            return criteriaBuilder.and(
                    predicates.toArray(new Predicate[0])
            );
        };
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
