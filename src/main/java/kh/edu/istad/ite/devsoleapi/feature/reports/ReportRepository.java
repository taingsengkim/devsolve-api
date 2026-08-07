package kh.edu.istad.ite.devsoleapi.feature.reports;


import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;

import java.util.UUID;

public interface ReportRepository
        extends JpaRepository<Report, UUID>,
        JpaSpecificationExecutor<Report> {

    @EntityGraph(attributePaths = {
            "program",
            "reporter",
            "weakness",
            "asset",
            "triagedBy",
            "duplicateOf"
    })
    Page<Report> findByReporterId(UUID reporterId, Pageable pageable);

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


    Page<Report> findByReporterIdAndState(
            UUID reporterId,
            ReportState state,
            Pageable pageable
    );
}
