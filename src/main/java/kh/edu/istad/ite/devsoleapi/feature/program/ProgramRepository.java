package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID>, JpaSpecificationExecutor<Program> {

    String PUBLIC_PROGRAM_FILTERS = """
            FROM public.programs p
            JOIN public.organizations o
              ON o.id = p.organization_id
            WHERE p.deleted_at IS NULL
              AND o.deleted_at IS NULL
              AND o.status = 'active'
              AND p.state = CAST('active' AS public.program_state_enum)
              AND p.submission_state =
                  CAST('approved' AS public.submission_state_enum)
              AND p.visibility = CAST('public' AS public.visibility_enum)
              AND (
                    :organizationId IS NULL
                    OR p.organization_id = :organizationId
              )
              AND (
                    :engagementType IS NULL
                    OR p.engagement_type = CAST(
                        :engagementType AS public.engagement_type_enum
                    )
              )
              AND (
                    :offersBounties IS NULL
                    OR p.offers_bounties = :offersBounties
              )
              AND (
                    :minimumBounty IS NULL
                    OR p.maximum_bounty >= :minimumBounty
              )
              AND (
                    :maximumBounty IS NULL
                    OR p.minimum_bounty <= :maximumBounty
              )
              AND (
                    :queryPattern IS NULL
                    OR LOWER(p.name) LIKE :queryPattern ESCAPE '\\'
                    OR LOWER(p.handle) LIKE :queryPattern ESCAPE '\\'
                    OR LOWER(p.description) LIKE :queryPattern ESCAPE '\\'
                    OR LOWER(o.name) LIKE :queryPattern ESCAPE '\\'
                    OR LOWER(o.slug) LIKE :queryPattern ESCAPE '\\'
              )
              AND (
                    :industry IS NULL
                    OR o.industry = CAST(
                        :industry AS public.industry_enum
                    )
              )
              AND (
                    :country IS NULL
                    OR LOWER(o.country) = :country
              )
              AND (
                    (:assetType IS NULL AND :maxSeverity IS NULL)
                    OR EXISTS (
                        SELECT 1
                        FROM public.program_assets asset
                        WHERE asset.program_id = p.id
                          AND asset.is_in_scope = TRUE
                          AND (
                                :assetType IS NULL
                                OR asset.asset_type = CAST(
                                    :assetType AS public.asset_type_enum
                                )
                          )
                          AND (
                                :maxSeverity IS NULL
                                OR asset.max_severity = CAST(
                                    :maxSeverity AS public.severity_enum
                                )
                          )
                    )
              )
            """;

    String PUBLIC_PROGRAM_ORDER = """
            ORDER BY
              CASE WHEN :sortProperty = 'publishedAt'
                         AND :sortDirection = 'ASC'
                   THEN p.published_at END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'publishedAt'
                         AND :sortDirection = 'DESC'
                   THEN p.published_at END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'createdAt'
                         AND :sortDirection = 'ASC'
                   THEN p.created_at END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'createdAt'
                         AND :sortDirection = 'DESC'
                   THEN p.created_at END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'updatedAt'
                         AND :sortDirection = 'ASC'
                   THEN p.updated_at END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'updatedAt'
                         AND :sortDirection = 'DESC'
                   THEN p.updated_at END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'name'
                         AND :sortDirection = 'ASC'
                   THEN LOWER(p.name) END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'name'
                         AND :sortDirection = 'DESC'
                   THEN LOWER(p.name) END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'handle'
                         AND :sortDirection = 'ASC'
                   THEN LOWER(p.handle) END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'handle'
                         AND :sortDirection = 'DESC'
                   THEN LOWER(p.handle) END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'minimumBounty'
                         AND :sortDirection = 'ASC'
                   THEN p.minimum_bounty END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'minimumBounty'
                         AND :sortDirection = 'DESC'
                   THEN p.minimum_bounty END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'maximumBounty'
                         AND :sortDirection = 'ASC'
                   THEN p.maximum_bounty END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'maximumBounty'
                         AND :sortDirection = 'DESC'
                   THEN p.maximum_bounty END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'viewCount'
                         AND :sortDirection = 'ASC'
                   THEN p.view_count END ASC NULLS LAST,
              CASE WHEN :sortProperty = 'viewCount'
                         AND :sortDirection = 'DESC'
                   THEN p.view_count END DESC NULLS LAST,
              CASE WHEN :sortProperty = 'followerCount'
                         AND :sortDirection = 'ASC'
                   THEN (
                       SELECT COUNT(*)
                       FROM public.follows follow_row
                       WHERE follow_row.followable_type = 'program'
                         AND follow_row.followable_id = p.id
                   ) END ASC,
              CASE WHEN :sortProperty = 'followerCount'
                         AND :sortDirection = 'DESC'
                   THEN (
                       SELECT COUNT(*)
                       FROM public.follows follow_row
                       WHERE follow_row.followable_type = 'program'
                         AND follow_row.followable_id = p.id
                   ) END DESC,
              CASE WHEN :sortProperty = 'totalSubmissions'
                         AND :sortDirection = 'ASC'
                   THEN (
                       SELECT COUNT(*)
                       FROM public.reports report
                       WHERE report.program_id = p.id
                   ) END ASC,
              CASE WHEN :sortProperty = 'totalSubmissions'
                         AND :sortDirection = 'DESC'
                   THEN (
                       SELECT COUNT(*)
                       FROM public.reports report
                       WHERE report.program_id = p.id
                   ) END DESC,
              CASE WHEN :sortProperty = 'id'
                         AND :sortDirection = 'ASC'
                   THEN p.id END ASC,
              CASE WHEN :sortProperty = 'id'
                         AND :sortDirection = 'DESC'
                   THEN p.id END DESC,
              p.id DESC
            """;

    Optional<Program> findByHandle(String handle);

    @Query(
            value = "SELECT p.*\n"
                    + PUBLIC_PROGRAM_FILTERS
                    + PUBLIC_PROGRAM_ORDER,
            countQuery = "SELECT COUNT(*)\n"
                    + PUBLIC_PROGRAM_FILTERS
                    + " AND CAST(:sortProperty AS text) IS NOT NULL\n"
                    + " AND CAST(:sortDirection AS text) IS NOT NULL\n",
            nativeQuery = true
    )
    Page<Program> searchPublicPrograms(
            @Param("organizationId") UUID organizationId,
            @Param("engagementType") String engagementType,
            @Param("offersBounties") Boolean offersBounties,
            @Param("queryPattern") String queryPattern,
            @Param("minimumBounty") java.math.BigDecimal minimumBounty,
            @Param("maximumBounty") java.math.BigDecimal maximumBounty,
            @Param("assetType") String assetType,
            @Param("maxSeverity") String maxSeverity,
            @Param("industry") String industry,
            @Param("country") String country,
            @Param("sortProperty") String sortProperty,
            @Param("sortDirection") String sortDirection,
            Pageable pageable
    );

    long countByOrganizationIdAndStateAndDeletedAtIsNull(
            UUID organizationId,
            ProgramState state
    );

    long countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
            UUID organizationId,
            ProgramState state,
            Visibility visibility
    );

    Optional<Program>
    findByIdAndStateAndSubmissionStateAndVisibilityAndDeletedAtIsNull(
            UUID id,
            ProgramState state,
            SubmissionState submissionState,
            Visibility visibility
    );

    boolean existsByHandleIgnoreCase(String handle);

    boolean existsByHandleIgnoreCaseAndIdNot(String handle, UUID id);

    Page<Program> findAll(
            Specification<Program> specification,
            Pageable pageable
    );

    @Query("""
            select count(distinct report.reporter.id) as totalResearchers,
                   count(report.id) as totalSubmissions
            from Report report
            where report.program.id = :programId
            """)
    PublicProgramStatistics findPublicStatisticsByProgramId(
            @Param("programId") UUID programId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Program program
            set program.viewCount = program.viewCount + 1
            where program.id = :id
              and program.state = :state
              and program.submissionState = :submissionState
              and program.visibility = :visibility
              and program.deletedAt is null
            """)
    int incrementPublicViewCount(
            @Param("id") UUID id,
            @Param("state") ProgramState state,
            @Param("submissionState") SubmissionState submissionState,
            @Param("visibility") Visibility visibility
    );

    @Query("""
            select program.viewCount
            from Program program
            where program.id = :id
              and program.state = :state
              and program.submissionState = :submissionState
              and program.visibility = :visibility
              and program.deletedAt is null
            """)
    Long findPublicViewCountById(
            @Param("id") UUID id,
            @Param("state") ProgramState state,
            @Param("submissionState") SubmissionState submissionState,
            @Param("visibility") Visibility visibility
    );

    @Query("""
            select count(program) as totalPrograms,
                   coalesce(sum(case when program.state = :draftState
                       then 1 else 0 end), 0) as draftPrograms,
                   coalesce(sum(case when program.state = :activeState
                       then 1 else 0 end), 0) as activePrograms,
                   coalesce(sum(case when program.state = :pausedState
                       then 1 else 0 end), 0) as pausedPrograms,
                   coalesce(sum(case when program.state = :closedState
                       then 1 else 0 end), 0) as closedPrograms,
                   coalesce(sum(case when program.submissionState =
                       :pendingSubmissionState
                       then 1 else 0 end), 0) as pendingReviewPrograms
            from Program program
            where program.deletedAt is null
            """)
    AdminProgramCounts findAdminCounts(
            @Param("draftState") ProgramState draftState,
            @Param("activeState") ProgramState activeState,
            @Param("pausedState") ProgramState pausedState,
            @Param("closedState") ProgramState closedState,
            @Param("pendingSubmissionState")
            SubmissionState pendingSubmissionState
    );

    List<Program> findByOrganizationId(UUID organizationId);

    interface PublicProgramStatistics {

        long getTotalResearchers();

        long getTotalSubmissions();
    }

    interface AdminProgramCounts {

        long getTotalPrograms();

        long getDraftPrograms();

        long getActivePrograms();

        long getPausedPrograms();

        long getClosedPrograms();

        long getPendingReviewPrograms();
    }
}
