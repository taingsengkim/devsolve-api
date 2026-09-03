package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

/**
 * The SQL behind the company analytics dashboard.
 *
 * <p>Held here rather than inline in {@code @Query} so the shared parts can be
 * written once. {@link #WHERE_SCOPE} above all: every figure on the dashboard
 * is bounded by one organization, optionally one program and one window of
 * submission dates, and a copy of that clause that drifted would serve one
 * company another's reports. Six copies is six chances to drift.
 *
 * <p>Every constant is a compile-time constant expression, which is what lets
 * an annotation reference it. That also fixes how the pieces may be joined:
 * a text block drops trailing white space from each line, so a fragment spliced
 * in after {@code WHERE } would arrive glued to it. Each splice therefore
 * happens at a line boundary, and each inlined predicate carries its own
 * trailing newline.
 *
 * <p>The queries are native because none of what they do has a JPQL spelling:
 * {@code date_trunc}, aggregate {@code FILTER} clauses and interval arithmetic.
 * Native results skip the entity's converters, so enum columns come back as
 * lower-case database text and are rebuilt by the caller, and every alias is
 * quoted to survive Postgres folding unquoted identifiers to lower case.
 */
public final class AnalyticsQueries {

    /**
     * How many rows the three "top N" lists return. Ten is what the dashboard
     * has room for, and the tail below it is noise on a chart.
     */
    public static final int TOP_LIST_SIZE = 10;

    private static final String SELECT_FROM = """
            FROM public.reports r
            JOIN public.programs p
                ON p.id = r.program_id
            """;

    /**
     * Payouts folded to one row per report before the join.
     *
     * <p>A report can carry more than one reward — a bounty and a later
     * correction — so joining {@code report_rewards} directly would multiply
     * the report by its payouts and count it twice in every {@code COUNT(*)}
     * standing beside the sum.
     */
    private static final String JOIN_REWARDS = """
            LEFT JOIN (
                SELECT reward.report_id AS report_id,
                       SUM(reward.amount) AS amount
                FROM public.report_rewards reward
                GROUP BY reward.report_id
            ) rw ON rw.report_id = r.id
            """;

    /**
     * The one scope clause.
     *
     * <p>Reports on a deleted program are left out throughout: the program is
     * gone from the organization's own listings, so counting its findings here
     * would put rows on the dashboard that cannot be opened from it.
     *
     * <p>{@code programId} is optional and cast explicitly — Postgres cannot
     * infer the type of a null parameter, and an uncast one fails the whole
     * statement rather than the filter.
     */
    private static final String WHERE_SCOPE = """
            WHERE p.organization_id = :organizationId
              AND p.deleted_at IS NULL
              AND (
                  CAST(:programId AS uuid) IS NULL
                  OR r.program_id = CAST(:programId AS uuid)
              )
              AND r.submitted_at >= :startDate
              AND r.submitted_at < :endDate
            """;

    /**
     * The best assessment a report carries: the settled severity, else
     * triage's call, else what the reporter claimed.
     *
     * <p>{@code severity} alone would be wrong for a dashboard. It stays null
     * while a severity disagreement is open and on every report nobody has
     * looked at yet, so a distribution built on it would hide the untriaged
     * backlog rather than show it.
     */
    private static final String EFFECTIVE_SEVERITY =
            "COALESCE(r.severity, r.triage_severity, r.reported_severity)";

    /**
     * Triage agreed the finding is real.
     *
     * <p>{@code retesting} belongs here: a report only reaches it after being
     * resolved, so leaving it out would un-accept a finding the moment the
     * organization asked for its fix to be checked. {@code duplicate} does
     * not — a duplicate is neither accepted nor rejected, and counts towards
     * neither rate.
     */
    private static final String ACCEPTED =
            "r.state IN ('valid_confirmed', 'retesting', 'resolved')\n";

    private static final String IS_CRITICAL =
            EFFECTIVE_SEVERITY + " = 'critical'\n";

    private static final String IS_HIGH = EFFECTIVE_SEVERITY + " = 'high'\n";

    /**
     * Whether the triage target has been settled for a report, one way or the
     * other: it was triaged, or it is still waiting and the deadline has
     * passed. A report still inside the target is undecided and belongs in
     * neither half of the compliance figure.
     */
    private static final String SLA_DECIDED = """
            r.triaged_at IS NOT NULL
                 OR r.submitted_at + (
                        CAST(:triageTargetHours AS integer) * INTERVAL '1 hour'
                    ) < :asOf
            """;

    private static final String SLA_MET = """
            r.triaged_at IS NOT NULL
                AND r.triaged_at <= r.submitted_at + (
                        CAST(:triageTargetHours AS integer) * INTERVAL '1 hour'
                    )
            """;

    /**
     * Every headline figure for one window, in one pass.
     *
     * <p>The two mean times need no {@code FILTER}: subtracting from a null
     * {@code triaged_at} or {@code resolved_at} yields null and {@code AVG}
     * skips nulls, so each average already covers only the reports that
     * reached that stage. Both are cast to {@code double precision} because
     * {@code EXTRACT(EPOCH …)} changed return type between Postgres versions
     * and a projection getter cannot be right for both.
     */
    public static final String SUMMARY = """
            SELECT COUNT(*) AS "totalReports",
                   COUNT(*) FILTER (WHERE
            """
            + ACCEPTED
            + """
                   ) AS "acceptedReports",
                   COUNT(*) FILTER (
                       WHERE r.state = 'rejected'
                   ) AS "rejectedReports",
                   COUNT(*) FILTER (
                       WHERE r.state = 'resolved'
                   ) AS "resolvedReports",
                   COUNT(DISTINCT r.reporter_id) AS "activeResearchers",
                   COALESCE(SUM(rw.amount), 0) AS "bountiesPaid",
                   COALESCE(SUM(r.reputation_points), 0) AS "reputationPoints",
                   COALESCE(CAST(AVG(
                       EXTRACT(EPOCH FROM (r.triaged_at - r.submitted_at)) / 3600
                   ) AS double precision), 0) AS "meanTriageHours",
                   COALESCE(CAST(AVG(
                       EXTRACT(EPOCH FROM (r.resolved_at - r.submitted_at)) / 86400
                   ) AS double precision), 0) AS "meanResolveDays",
                   COUNT(*) FILTER (WHERE
            """
            + SLA_DECIDED
            + """
                   ) AS "slaDecidedReports",
                   COUNT(*) FILTER (WHERE
            """
            + SLA_MET
            + """
                   ) AS "slaMetReports",
                   to_char(MIN(r.submitted_at), 'YYYY-MM-DD') AS "firstSubmittedOn"
            """
            + SELECT_FROM
            + JOIN_REWARDS
            + WHERE_SCOPE;

    /**
     * Inflow, acceptance and resolution per bucket.
     *
     * <p>Only the buckets holding a report come back. The caller fills the
     * gaps, because a chart has to show a quiet month rather than close it up.
     *
     * <p>Bounties are bucketed by the report's submission date, not by when
     * they were paid: bucketing by payment would put a bounty in a different
     * period from the finding it settles, and could put it outside the window
     * altogether.
     *
     * <p>Grouped by output position rather than by repeating the expression.
     * Hibernate expands a named parameter into one placeholder per occurrence,
     * so a {@code GROUP BY date_trunc(CAST(:bucket AS text), …)} is a
     * different expression to Postgres than the identical-looking one in the
     * select list — and the query is rejected for selecting an ungrouped
     * column. The bucket key is formatted {@code YYYY-MM-DD}, so ordering by
     * that same position is chronological.
     */
    public static final String SUBMISSION_TREND = """
            SELECT to_char(
                       date_trunc(CAST(:bucket AS text), r.submitted_at),
                       'YYYY-MM-DD'
                   ) AS "bucketStart",
                   COUNT(*) AS "submitted",
                   COUNT(*) FILTER (WHERE
            """
            + ACCEPTED
            + """
                   ) AS "accepted",
                   COUNT(*) FILTER (
                       WHERE r.state = 'resolved'
                   ) AS "resolved",
                   COUNT(*) FILTER (
                       WHERE r.state = 'rejected'
                   ) AS "rejected",
                   COALESCE(SUM(rw.amount), 0) AS "bountyPaid"
            """
            + SELECT_FROM
            + JOIN_REWARDS
            + WHERE_SCOPE
            + """
            GROUP BY 1
            ORDER BY 1
            """;

    /**
     * Reports and average payout per severity band.
     *
     * <p>The average is over the pre-aggregated payout, which is null for a
     * report that was never paid — so {@code AVG} skips it rather than
     * averaging it in as zero. That makes the figure answer what a finding in
     * this band has actually been worth.
     */
    public static final String SEVERITY_DISTRIBUTION = """
            SELECT CAST(
            """
            + EFFECTIVE_SEVERITY
            + """
                   AS text) AS "severity",
                   COUNT(*) AS "total",
                   COALESCE(AVG(rw.amount), 0) AS "averageBounty"
            """
            + SELECT_FROM
            + JOIN_REWARDS
            + WHERE_SCOPE
            + """
            GROUP BY
            """
            + EFFECTIVE_SEVERITY;

    /**
     * The weakness classes this organization actually receives.
     *
     * <p>An inner join on the catalog, so a report triage has not classified
     * belongs to no class here. It stays in the denominator the caller divides
     * by, which is why these percentages are not expected to sum to 100.
     */
    public static final String TOP_WEAKNESSES = """
            SELECT w.cwe_id AS "cweId",
                   w.name AS "name",
                   COUNT(*) AS "total",
                   COUNT(*) FILTER (WHERE
            """
            + IS_CRITICAL
            + """
                   ) AS "criticalCount"
            """
            + SELECT_FROM
            + """
            JOIN public.weaknesses w
                ON w.id = r.weakness_id
            """
            + WHERE_SCOPE
            + """
            GROUP BY w.cwe_id, w.name
            ORDER BY COUNT(*) DESC, w.name ASC
            LIMIT :maxResults
            """;

    /**
     * Where the findings are landing.
     *
     * <p>Grouped by identifier and type rather than by asset row: the same
     * host declared in scope on three programs is one target to the team that
     * has to defend it.
     */
    public static final String TOP_ASSETS = """
            SELECT a.identifier AS "assetTarget",
                   CAST(a.asset_type AS text) AS "assetType",
                   COUNT(*) AS "totalReports",
                   COUNT(*) FILTER (WHERE
            """
            + IS_CRITICAL
            + """
                   ) AS "criticalCount",
                   COUNT(*) FILTER (WHERE
            """
            + IS_HIGH
            + """
                   ) AS "highCount",
                   COALESCE(SUM(rw.amount), 0) AS "totalBounty"
            """
            + SELECT_FROM
            + """
            JOIN public.program_assets a
                ON a.id = r.asset_id
            """
            + JOIN_REWARDS
            + WHERE_SCOPE
            + """
            GROUP BY a.identifier, a.asset_type
            ORDER BY COUNT(*) DESC, a.identifier ASC
            LIMIT :maxResults
            """;

    /**
     * The researchers the organization owes the most to over the window.
     *
     * <p>Accepted findings only, ordered by how many of them there are before
     * what they were paid: a company reads this to know who to keep inviting,
     * and that is a question about findings rather than about its own bounty
     * table.
     */
    public static final String TOP_RESEARCHERS = """
            SELECT u.id AS "userId",
                   u.username AS "username",
                   u.full_name AS "displayName",
                   u.avatar_url AS "avatarUrl",
                   COUNT(*) AS "validReports",
                   COUNT(*) FILTER (WHERE
            """
            + IS_CRITICAL
            + """
                   ) AS "criticalReports",
                   COALESCE(SUM(rw.amount), 0) AS "totalBountiesEarned",
                   COALESCE(SUM(r.reputation_points), 0) AS "reputationEarned"
            """
            + SELECT_FROM
            + """
            JOIN public.user_profiles u
                ON u.id = r.reporter_id
            """
            + JOIN_REWARDS
            + WHERE_SCOPE
            + """
              AND
            """
            + ACCEPTED
            + """
            GROUP BY u.id, u.username, u.full_name, u.avatar_url
            ORDER BY COUNT(*) DESC,
                     COALESCE(SUM(rw.amount), 0) DESC,
                     u.username ASC
            LIMIT :maxResults
            """;

    private AnalyticsQueries() {
    }
}
