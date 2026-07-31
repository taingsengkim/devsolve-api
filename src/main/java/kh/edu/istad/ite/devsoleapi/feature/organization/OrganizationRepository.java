package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    Optional<Organization> findByIdAndStatusAndDeletedAtIsNull(UUID id, OrganizationStatus status);

    Optional<Organization> findBySlugAndStatusAndDeletedAtIsNull(String slug, OrganizationStatus status);

    Optional<Organization> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    Page<Organization> findByStatusAndDeletedAtIsNull(
            OrganizationStatus status,
            Pageable pageable
    );
}
