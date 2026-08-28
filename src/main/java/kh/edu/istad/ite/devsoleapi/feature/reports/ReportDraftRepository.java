package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportDraftRepository
        extends JpaRepository<ReportDraft, UUID> {

    /**
     * Ownership is part of the lookup rather than a check afterwards, so a
     * draft belonging to someone else is indistinguishable from one that does
     * not exist. A reporter has no business learning that another reporter is
     * drafting against a program.
     */
    Optional<ReportDraft> findByIdAndReporterId(UUID id, UUID reporterId);

    Page<ReportDraft> findByReporterId(UUID reporterId, Pageable pageable);

    Page<ReportDraft> findByReporterIdAndProgramId(
            UUID reporterId,
            UUID programId,
            Pageable pageable
    );

    long countByReporterIdAndProgramId(UUID reporterId, UUID programId);
}
