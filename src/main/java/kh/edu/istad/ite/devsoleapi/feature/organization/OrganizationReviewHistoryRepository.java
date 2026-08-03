package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationReviewHistoryRepository
        extends JpaRepository<OrganizationReviewHistory, UUID> {

    Page<OrganizationReviewHistory> findByOrganization_Id(
            UUID organizationId,
            Pageable pageable
    );
}
