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

    /**
     * What one researcher has been paid, across every program.
     *
     * <p>Computed rather than kept as a running total on the profile. A payout
     * can be corrected or withdrawn, and a stored total that was only ever
     * added to would keep money on a profile that no longer has it — the same
     * drift that left the report counters wrong. One aggregate over a
     * researcher's own rewards is cheap and cannot disagree with the rewards
     * themselves.
     *
     * <p>{@code amount} is nullable, since a reward can be points-only, and
     * sum over no rows is NULL — so both sums are coalesced to zero. The
     * report count is distinct because one report can carry several payouts
     * and a researcher paid twice for one finding has still had one report
     * rewarded.
     */
    @Query("""
            select coalesce(sum(reward.amount), 0) as totalEarned,
                   coalesce(sum(reward.points), 0) as totalPoints,
                   count(distinct reward.report.id) as rewardedReports
            from ReportReward reward
            where reward.report.reporter.id = :userId
            """)
    ResearcherEarnings findResearcherEarnings(@Param("userId") UUID userId);

    interface ResearcherEarnings {

        BigDecimal getTotalEarned();

        long getTotalPoints();

        long getRewardedReports();
    }
}
