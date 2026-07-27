package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;
import java.util.UUID;

public final class ReportSpecification {

    private ReportSpecification() {
    }

    public static Specification<Report> forProgram(UUID programId) {
        return (root, query, criteriaBuilder) -> programId == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(
                        root.get("program").get("id"),
                        programId
                );
    }

    public static Specification<Report> withState(ReportState state) {
        return (root, query, criteriaBuilder) -> state == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("state"), state);
    }

    public static Specification<Report> submittedBy(UUID reporterId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("reporter").get("id"),
                        reporterId
                );
    }

    public static Specification<Report> forOrganizations(
            Set<UUID> organizationIds
    ) {
        return (root, query, criteriaBuilder) -> {
            if (organizationIds.isEmpty()) {
                return criteriaBuilder.disjunction();
            }
            return root.get("program")
                    .get("organizationId")
                    .in(organizationIds);
        };
    }
}
