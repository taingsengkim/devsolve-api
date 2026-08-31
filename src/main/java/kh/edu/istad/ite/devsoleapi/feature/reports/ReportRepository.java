package kh.edu.istad.ite.devsoleapi.feature.reports;


import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReportRepository
        extends JpaRepository<Report, UUID>,
        JpaSpecificationExecutor<Report> {

    /**
     * Counted across every program the organization runs, deleted ones aside.
     * A resolved finding stays to the organization's credit even after the
     * program that surfaced it is paused or closed.
     */
    @Query("""
            select count(report)
            from Report report
            where report.program.organizationId = :organizationId
              and report.program.deletedAt is null
              and report.state = :state
            """)
    long countByOrganizationAndState(
            @Param("organizationId") UUID organizationId,
            @Param("state") ReportState state
    );

    @Query("""
            select report.program.id as id,
                   count(report.id) as total
            from Report report
            where report.program.id in :programIds
            group by report.program.id
            """)
    List<IdCountProjection> countByProgramIds(
            @Param("programIds") Collection<UUID> programIds
    );

    @EntityGraph(attributePaths = {
            "program",
            "reporter",
            "weakness",
            "asset",
            "triagedBy",
            "duplicateOf"
    })
    Page<Report> findByReporterId(UUID reporterId, Pageable pageable);

    /**
     * Feeds the sustained half of {@link ReportRateLimiter}. Counted across
     * every program, and across every state: a report withdrawn or rejected
     * after the fact still cost the triage queue the read.
     */
    @Query("""
            select count(report)
            from Report report
            where report.reporter.id = :reporterId
              and report.submittedAt >= :since
            """)
    long countByReporterSince(
            @Param("reporterId") UUID reporterId,
            @Param("since") LocalDateTime since
    );

    @Override
    @EntityGraph(attributePaths = {
            "program",
            "reporter",
            "weakness",
            "asset",
            "triagedBy",
            "duplicateOf"
    })
    Page<Report> findAll(
            Specification<Report> specification,
            Pageable pageable
    );


    /**
     * Guards the deletion of a weakness from the catalog. Counts every report
     * classified under it whatever its state, because each one holds the
     * foreign key the delete would have to break.
     */
    long countByWeaknessId(UUID weaknessId);


    Page<Report> findByReporterIdAndStateAndDisclosureStatus(
            UUID reporterId,
            ReportState state,
            DisclosureStatus disclosureStatus,
            Pageable pageable
    );



    @Query("""
            select count(report) as totalReports,
                   coalesce(sum(case when report.state = :newState
                       then 1 else 0 end), 0) as newReports,
                   coalesce(sum(case when report.state = :triagingState
                       then 1 else 0 end), 0) as triagingReports,
                   coalesce(sum(case when report.state = :needsMoreInfoState
                       then 1 else 0 end), 0) as needsMoreInfoReports,
                   coalesce(sum(case when report.state = :validConfirmedState
                       then 1 else 0 end), 0) as validConfirmedReports,
                   coalesce(sum(case when report.state = :resolvedState
                       then 1 else 0 end), 0) as resolvedReports,
                   coalesce(sum(case when report.state = :rejectedState
                       then 1 else 0 end), 0) as rejectedReports,
                   coalesce(sum(case when report.state = :duplicateState
                       then 1 else 0 end), 0) as duplicateReports
            from Report report
            """)
    AdminReportCounts findAdminCounts(
            @Param("newState") ReportState newState,
            @Param("triagingState") ReportState triagingState,
            @Param("needsMoreInfoState") ReportState needsMoreInfoState,
            @Param("validConfirmedState") ReportState validConfirmedState,
            @Param("resolvedState") ReportState resolvedState,
            @Param("rejectedState") ReportState rejectedState,
            @Param("duplicateState") ReportState duplicateState
    );

    interface AdminReportCounts {

        long getTotalReports();

        long getNewReports();

        long getTriagingReports();

        long getNeedsMoreInfoReports();

        long getValidConfirmedReports();

        long getResolvedReports();

        long getRejectedReports();

        long getDuplicateReports();
    }
}
