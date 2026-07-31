package kh.edu.istad.ite.devsoleapi.feature.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<Organization> findByIdAndStatusAndDeletedAtIsNull(UUID id, OrganizationStatus status);

    Optional<Organization> findBySlugAndStatusAndDeletedAtIsNull(String slug, OrganizationStatus status);

    Optional<Organization> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    Optional<Organization> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select organization
            from Organization organization
            where organization.id = :id
              and organization.deletedAt is null
            """)
    Optional<Organization> findByIdForReview(@Param("id") UUID id);

    Page<Organization> findByStatusAndDeletedAtIsNull(
            OrganizationStatus status,
            Pageable pageable
    );
}
