package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository
        extends JpaRepository<Dispute, UUID>,
        JpaSpecificationExecutor<Dispute> {

    Optional<Dispute> findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
            UUID reportId,
            Collection<DisputeStatus> statuses
    );

    /**
     * Severity disagreements the reporter never answered, oldest deadline
     * first.
     *
     * <p>Ids rather than entities so the sweep can settle each one in its own
     * transaction: a row that cannot be settled should cost that report its
     * deadline, not every other report's.
     */
    @Query("""
            select dispute.id
            from Dispute dispute
            where dispute.status = :status
              and dispute.respondBy is not null
              and dispute.respondBy < :now
            order by dispute.respondBy asc
            """)
    List<UUID> findOverdueIds(
            @Param("status") DisputeStatus status,
            @Param("now") LocalDateTime now
    );
}
