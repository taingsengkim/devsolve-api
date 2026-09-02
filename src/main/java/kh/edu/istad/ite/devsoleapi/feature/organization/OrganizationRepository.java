package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.persistence.LockModeType;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @EntityGraph(attributePaths = "owner")
    @Query("""
            select organization
            from Organization organization
            where organization.deletedAt is null
              and (
                    :status is null
                    or organization.status = :status
              )
              and (
                    :queryPattern is null
                    or lower(organization.name) like :queryPattern
                    or lower(organization.slug) like :queryPattern
                    or lower(organization.websiteUrl) like :queryPattern
                    or lower(organization.country) like :queryPattern
                    or lower(organization.owner.fullName) like :queryPattern
                    or lower(organization.owner.email) like :queryPattern
              )
            """)
    Page<Organization> findForAdmin(
            @Param("queryPattern") String queryPattern,
            @Param("status") OrganizationStatus status,
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

    /**
     * Every organization changed at or after the cursor, oldest first, for the
     * search index. Unfiltered so that a suspension or a soft delete reaches
     * the indexer as a removal — see {@code ProgramRepository#findChangedSince}.
     *
     * <p>Keyed on {@code (updated_at, id)} rather than paged by offset, for the
     * reason set out on {@code SyncCursor}: rows here are ordered by when they
     * changed, and one of them changing mid-pass shifts every row behind it
     * down one, which under an offset skips a row silently. A cursor starting
     * at {@code (since, nil uuid)} reads the same rows an
     * {@code updated_at >= since} would.
     */
    @Query("""
            select organization
            from Organization organization
            where organization.updatedAt > :changedAt
               or (organization.updatedAt = :changedAt and organization.id > :id)
            order by organization.updatedAt asc, organization.id asc
            """)
    Slice<Organization> findChangedSince(
            @Param("changedAt") LocalDateTime changedAt,
            @Param("id") UUID id,
            Pageable pageable
    );

    interface AdminOrganizationCounts {

        long getTotalOrganizations();

        long getPendingOrganizations();

        long getActiveOrganizations();

        long getRejectedOrganizations();
    }
}
