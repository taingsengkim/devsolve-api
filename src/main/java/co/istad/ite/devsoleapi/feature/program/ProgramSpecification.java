package co.istad.ite.devsoleapi.feature.program;

import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProgramSpecification {

    public static Specification<Program> filterPrograms(
            UUID organizationId,
            ProgramState state,
            Visibility visibility,
            EngagementType engagementType,
            Boolean offersBounties
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (organizationId != null) {
                predicates.add(criteriaBuilder.equal(root.get("organizationId"), organizationId));
            }
            if (state != null) {
                predicates.add(criteriaBuilder.equal(root.get("state"), state));
            }
            if (visibility != null) {
                predicates.add(criteriaBuilder.equal(root.get("visibility"), visibility));
            }
            if (engagementType != null) {
                predicates.add(criteriaBuilder.equal(root.get("engagementType"), engagementType));
            }
            if (offersBounties != null) {
                predicates.add(criteriaBuilder.equal(root.get("offersBounties"), offersBounties));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}