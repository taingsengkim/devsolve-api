package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationResearcherRepository
        extends JpaRepository<OrganizationResearcher, UUID> {

    @EntityGraph(attributePaths = {"organization", "researcher"})
    Optional<OrganizationResearcher> findByOrganizationIdAndResearcherId(
            UUID organizationId,
            UUID researcherId
    );

    boolean existsByOrganizationIdAndResearcherIdAndStatus(
            UUID organizationId,
            UUID researcherId,
            ResearcherAccessStatus status
    );

    @EntityGraph(attributePaths = {"organization", "researcher"})
    Page<OrganizationResearcher> findByOrganizationId(
            UUID organizationId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "researcher"})
    Page<OrganizationResearcher> findByOrganizationIdAndStatus(
            UUID organizationId,
            ResearcherAccessStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "researcher"})
    Page<OrganizationResearcher> findByResearcherId(
            UUID researcherId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "researcher"})
    Page<OrganizationResearcher> findByResearcherIdAndStatus(
            UUID researcherId,
            ResearcherAccessStatus status,
            Pageable pageable
    );

    long countByOrganizationIdAndStatus(
            UUID organizationId,
            ResearcherAccessStatus status
    );
}
