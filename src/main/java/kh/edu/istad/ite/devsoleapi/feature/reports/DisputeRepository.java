package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Optional<Dispute> findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
            UUID reportId,
            Collection<DisputeStatus> statuses
    );
}
