package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportAttachmentRepository
        extends JpaRepository<ReportAttachment, UUID> {

    Optional<ReportAttachment> findByIdAndReportId(
            UUID id,
            UUID reportId
    );

    long countByReportId(UUID reportId);
}
