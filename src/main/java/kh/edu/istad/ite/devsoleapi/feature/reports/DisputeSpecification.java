package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

public final class DisputeSpecification {

    private DisputeSpecification() {
    }

    public static Specification<Dispute> withStatus(DisputeStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Dispute> withStatusIn(
            Collection<DisputeStatus> statuses
    ) {
        return (root, query, criteriaBuilder) ->
                statuses == null || statuses.isEmpty()
                        ? criteriaBuilder.conjunction()
                        : root.get("status").in(statuses);
    }

    public static Specification<Dispute> forProgram(UUID programId) {
        return (root, query, criteriaBuilder) -> programId == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(
                        root.get("report").get("program").get("id"),
                        programId
                );
    }

    public static Specification<Dispute> forReport(UUID reportId) {
        return (root, query, criteriaBuilder) -> reportId == null
                ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(
                        root.get("report").get("id"),
                        reportId
                );
    }
}
