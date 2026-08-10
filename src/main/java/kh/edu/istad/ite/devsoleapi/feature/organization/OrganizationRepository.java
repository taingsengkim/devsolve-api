package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.persistence.LockModeType;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select count(organization) as totalOrganizations,
                   coalesce(sum(case when organization.status = :pendingStatus
                       then 1 else 0 end), 0) as pendingOrganizations,
                   coalesce(sum(case when organization.status = :activeStatus
                       then 1 else 0 end), 0) as activeOrganizations,
                   coalesce(sum(case when organization.status = :rejectedStatus
                       then 1 else 0 end), 0) as rejectedOrganizations
            from Organization organization
            where organization.deletedAt is null
            """)
    AdminOrganizationCounts findAdminCounts(
            @Param("pendingStatus") OrganizationStatus pendingStatus,
            @Param("activeStatus") OrganizationStatus activeStatus,
            @Param("rejectedStatus") OrganizationStatus rejectedStatus
    );

    interface AdminOrganizationCounts {

        long getTotalOrganizations();

        long getPendingOrganizations();

        long getActiveOrganizations();

        long getRejectedOrganizations();
    }
}
