package kh.edu.istad.ite.devsoleapi.feature.platformstats;

/**
 * The SQL behind the four numbers on the home page.
 *
 * <p>Each query returns the same two columns — a {@code YYYY-MM} month key and
 * that month's delta — over the whole history of the platform, and never a
 * running total. The caller adds them up, which is what lets one query answer
 * both the headline figure and the twelve points of the sparkline underneath
 * it: the headline is every delta summed, and each point is every delta up to
 * that month. Asking the database for a cumulative series instead would be
 * twelve correlated aggregates per metric, and a separate query for the
 * headline that could disagree with the last point of its own chart.
 *
 * <p>Three of the four are cumulative facts about what has happened —
 * money paid, researchers who have taken part, findings confirmed — and are
 * deliberately not reduced when a program is deleted afterwards. A payout is
 * still a payout, and a counter on a landing page that falls because somebody
 * tidied up their programs is worse than one that is slightly generous.
 * {@link #LIVE_PROGRAMS_BY_MONTH} is the exception, and has to be: it counts
 * what is live now.
 *
 * <p>The queries are native for the same reason the analytics ones are:
 * {@code date_trunc} and {@code to_char} have no JPQL spelling. Aliases are
 * quoted so Postgres does not fold them to lower case out from under the
 * projection getters.
 */
public final class PlatformStatsQueries {

    /**
     * Every bounty ever paid, by the month it was awarded.
     *
     * <p>Points-only rewards carry a null amount and are skipped by
     * {@code SUM}, so a month of nothing but reputation awards contributes a
     * row of zero rather than dragging the total.
     */
    public static final String DISBURSED_BY_MONTH = """
            SELECT to_char(
                       date_trunc('month', reward.awarded_at),
                       'YYYY-MM'
                   ) AS "bucket",
                   COALESCE(SUM(reward.amount), 0) AS "delta"
            FROM public.report_rewards reward
            GROUP BY 1
            ORDER BY 1
            """;

    /**
     * Researchers, counted once each in the month they first submitted
     * anything.
     *
     * <p>"Active" is the profile's own status: a suspended or removed account
     * is not somebody a visitor could go and read. Counting sign-ups instead
     * would make the number a measure of registration rather than of research,
     * and every empty account would be in it.
     */
    public static final String RESEARCHERS_BY_MONTH = """
            SELECT to_char(
                       date_trunc('month', arrival.first_submitted_at),
                       'YYYY-MM'
                   ) AS "bucket",
                   COUNT(*) AS "delta"
            FROM (
                SELECT r.reporter_id,
                       MIN(r.submitted_at) AS first_submitted_at
                FROM public.reports r
                JOIN public.user_profiles u
                    ON u.id = r.reporter_id
                WHERE u.status = 'active'
                GROUP BY r.reporter_id
            ) arrival
            GROUP BY 1
            ORDER BY 1
            """;

    /**
     * The programs a visitor can open right now, by the month each went live.
     *
     * <p>The filters are the public listing's own, so this counts exactly what
     * {@code GET /api/v1/programs} would return.
     *
     * <p>Read the series with care: nothing on this platform records when a
     * program stopped being live, so a program paused or closed last March is
     * absent from every point of the chart rather than present in the earlier
     * ones. The series is therefore "programs live today, by when they went
     * live" — never falling, and an undercount of any past month in which
     * something has since been withdrawn. The last point is exact, because it
     * is the same set the headline counts.
     *
     * <p>{@code created_at} stands in for a missing {@code published_at}, which
     * only a row predating the publish stamp can have. Dropping those would
     * take them out of the headline too, since the headline is this query
     * summed.
     */
    public static final String LIVE_PROGRAMS_BY_MONTH = """
            SELECT to_char(
                       date_trunc(
                           'month',
                           COALESCE(p.published_at, p.created_at)
                       ),
                       'YYYY-MM'
                   ) AS "bucket",
                   COUNT(*) AS "delta"
            FROM public.programs p
            JOIN public.organizations o
                ON o.id = p.organization_id
            WHERE p.deleted_at IS NULL
              AND o.deleted_at IS NULL
              AND o.status = 'active'
              AND p.state = 'active'
              AND p.submission_state = 'approved'
              AND p.visibility = 'public'
            GROUP BY 1
            ORDER BY 1
            """;

    /**
     * Findings triage agreed were real, by the month that decision landed.
     *
     * <p>The three states match what the company dashboard calls accepted:
     * {@code retesting} is reached only from {@code resolved}, so leaving it
     * out would un-validate a finding the moment its fix was sent back to the
     * researcher to check. A duplicate is neither validated nor rejected and is
     * in neither figure.
     *
     * <p>Bucketed by {@code triaged_at}, falling back to the resolution and
     * then to the submission for rows that predate the triage stamp. This is
     * when the finding was confirmed, not when it was reported, which is the
     * number the counter claims to show.
     */
    public static final String VALIDATED_REPORTS_BY_MONTH = """
            SELECT to_char(
                       date_trunc(
                           'month',
                           COALESCE(r.triaged_at, r.resolved_at, r.submitted_at)
                       ),
                       'YYYY-MM'
                   ) AS "bucket",
                   COUNT(*) AS "delta"
            FROM public.reports r
            WHERE r.state IN ('valid_confirmed', 'retesting', 'resolved')
            GROUP BY 1
            ORDER BY 1
            """;

    private PlatformStatsQueries() {
    }
}
