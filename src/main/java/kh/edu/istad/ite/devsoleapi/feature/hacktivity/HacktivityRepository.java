package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Every hacktivity entry is rendered with its researcher, organization,
 * report, recognition and program, and all five associations are lazy. Left to
 * itself that is five extra selects per row — fifty-one queries to render a
 * page of ten.
 *
 * <p>Those five used to be pulled in with an {@code @EntityGraph} on the
 * paginated finders themselves. The page is read in two steps now, because a
 * fetch graph and a filtered page do not combine: the filters have to be
 * applied through a {@link org.springframework.data.jpa.domain.Specification},
 * and a specification is used to build the {@code Page}'s count query as well
 * as its content query — which is the one place a fetch join must not appear.
 *
 * <ol>
 *   <li>{@link JpaSpecificationExecutor#findAll} pages over the root alone.
 *       No fetch joins, so paging and counting are plain SQL over one table.
 *   <li>{@link #findAllWithAssociations} loads that page's ids once, with the
 *       joins. It is unpaginated, which is the condition a fetch join is
 *       actually safe under, and it returns the same managed instances the
 *       first step handed back — so the page's own rows come back hydrated.
 * </ol>
 *
 * <p>Two queries per page, fixed, whatever the page size.
 *
 * <p>Worth being clear about what this did <em>not</em> fix: the feed's 500 in
 * production was never the entity graph. Hibernate names every column of an
 * entity in its SELECT, and {@code reports} on a long-lived database was
 * missing several the {@code Report} entity declares — so every read of a
 * Report failed, rows or no rows, and this feed is the only public endpoint
 * that reads one. The columns are added in schema.sql; see the block above
 * {@code public.reports}.
 */
public interface HacktivityRepository
        extends JpaRepository<Hacktivity, UUID>,
        JpaSpecificationExecutor<Hacktivity> {

    /**
     * Step two above. {@code weakness} and {@code recognition} are left-joined
     * because a report may never have been classified and a row may be about
     * a resolution or a disclosure rather than a recognition; the other four
     * are NOT NULL.
     *
     * <p>An inner join on the recognition would drop exactly those rows from
     * the hydration pass while leaving them in the page — present, unloaded,
     * and failing on the first association the mapper touched.
     */
    @Query("""
            select hacktivity
            from Hacktivity hacktivity
              join fetch hacktivity.user
              join fetch hacktivity.organization
              join fetch hacktivity.report report
              join fetch hacktivity.program
              left join fetch hacktivity.recognition
              left join fetch report.weakness
            where hacktivity.id in :ids
            """)
    List<Hacktivity> findAllWithAssociations(
            @Param("ids") Collection<UUID> ids
    );

    /**
     * The payout on each of a page's reports, totalled.
     *
     * <p>Read separately rather than fetched with the row because rewards are
     * a collection: join fetching it would multiply a twice-paid report into
     * two feed rows, and doing that under pagination is the one thing a fetch
     * join must not do. One query per page either way.
     */
    @Query("""
            select reward.report.id as reportId,
                   sum(reward.amount) as amount,
                   sum(reward.points) as points
            from ReportReward reward
            where reward.report.id in :reportIds
            group by reward.report.id
            """)
    List<ReportPayout> findPayoutsByReportIds(
            @Param("reportIds") Collection<UUID> reportIds
    );

    /**
     * Three of the four header numbers in one pass. Counting distinct rather
     * than counting rows: a researcher with forty findings is one researcher.
     */
    @Query("""
            select count(hacktivity) as entries,
                   count(distinct hacktivity.user.id) as researchers,
                   count(distinct hacktivity.program.id) as programs
            from Hacktivity hacktivity
            """)
    FeedTotals findFeedTotals();

    /**
     * The fourth. Summed over the reports that reached the feed rather than
     * over every payout on the platform, so it answers "paid out for what is
     * shown here" — coalesced because a sum over no rows is null and the badge
     * should read zero.
     */
    @Query("""
            select coalesce(sum(reward.amount), 0)
            from ReportReward reward
            where reward.report.id in (
                select hacktivity.report.id from Hacktivity hacktivity
            )
            """)
    BigDecimal sumPaidOut();

    /**
     * Recognitions per researcher per severity since a cut-off, for the
     * windowed leaderboards.
     *
     * <p>The points themselves are deliberately not computed here.
     * {@code ReputationPolicy} is the single place that prices a severity, and
     * a CASE expression mirroring it in JPQL is a second copy that drifts the
     * day the curve is retuned. The window is small enough that folding the
     * counts in Java costs nothing.
     *
     * <p>Recognition-bearing rows only. The feed also carries resolutions and
     * disclosures, and a report that was resolved, then recognised, has two
     * rows — counting both would pay the researcher twice for one finding and
     * silently double every windowed score.
     */
    @Query("""
            select hacktivity.user.id as userId,
                   hacktivity.report.severity as severity,
                   count(hacktivity) as recognitions
            from Hacktivity hacktivity
            where hacktivity.createdAt >= :since
              and hacktivity.recognition is not null
            group by hacktivity.user.id, hacktivity.report.severity
            """)
    List<SeverityTally> tallyRecognitionsSince(
            @Param("since") LocalDateTime since
    );

    interface ReportPayout {

        UUID getReportId();

        /** Null when every reward on the report was points-only. */
        BigDecimal getAmount();

        /** Long because a JPQL sum over an int column widens. */
        Long getPoints();
    }

    interface FeedTotals {

        long getEntries();

        long getResearchers();

        long getPrograms();
    }

    interface SeverityTally {

        UUID getUserId();

        /** Null while a severity dispute on the report is open. */
        Severity getSeverity();

        long getRecognitions();
    }
}
