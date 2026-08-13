package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReportRewardRepository extends JpaRepository<ReportReward, UUID> {

    /**
     * Both payout figures in one pass — a report can carry several rewards, so
     * these cannot be folded into the resolved-report count without the join
     * multiplying that count by the number of payouts.
     *
     * <p>{@code amount} is nullable (a reward can be points-only), and sum and
     * max over no rows are both NULL, so each is coalesced to zero: a profile
     * with no payouts should read 0, not null.
     */
    @Query("""
            select coalesce(sum(reward.amount), 0) as totalDisbursed,
                   coalesce(max(reward.amount), 0) as topAward
            from ReportReward reward
            where reward.report.program.organizationId = :organizationId
              and reward.report.program.deletedAt is null
            """)
    OrganizationPayouts findOrganizationPayouts(
            @Param("organizationId") UUID organizationId
    );

    interface OrganizationPayouts {

        BigDecimal getTotalDisbursed();

        BigDecimal getTopAward();
    }
}
