package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportRewardRepository extends JpaRepository<ReportReward, UUID> {
    List<ReportReward> findByReportId(UUID reportId);
}
