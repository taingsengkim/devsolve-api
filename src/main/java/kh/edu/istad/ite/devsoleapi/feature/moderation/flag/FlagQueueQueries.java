package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

/**
 * The SQL behind the moderation queue.
 *
 * <p>A flag stores a type and an ID and nothing else about what was reported,
 * which left every caller to fetch the reported thing itself. On a page of
 * twenty that is twenty more round trips, one per row, each against a different
 * table — and each one 404s the moment a moderator takes the content down,
 * so the queue answered "what did I just remove?" with an empty box. These
 * queries resolve the content alongside the flag instead, in one statement.
 *
 * <p>Native, and not only because {@code FILTER}, {@code string_agg} and
 * {@code LATERAL} have no JPQL spelling. The five flaggable kinds live in five
 * unrelated tables with no mapped association between them and a flag — JPQL
 * cannot join what the model does not relate. Native SQL also reads straight
 * past the {@code @SQLRestriction} on problems and solutions, which is exactly
 * what a moderation queue needs: the row a moderator is judging is often one
 * that has just been hidden from everybody else.
 *
 * <p>Aliases are quoted so Postgres does not fold them to lower case out from
 * under the projection getters. Every nullable filter is compared through
 * {@code CAST(:param AS ...)}: the driver cannot infer a type for a bare null,
 * and Postgres refuses a parameter whose type it cannot resolve.
 */
public final class FlagQueueQueries {

    /**
     * The reported content itself, one branch per flaggable kind.
     *
     * <p>Every branch is keyed on the flag's own type as well as its ID, so
     * four of the five are eliminated by a constant-false filter and the fifth
     * is a primary-key lookup. A page of twenty flags costs twenty such
     * lookups, which is what makes this cheaper than the twenty HTTP round
     * trips it replaces.
     *
     * <p>The branches must agree on column count, order and type. Where a kind
     * has nothing to offer for a column the null is cast, since an untyped null
     * in the first branch would decide the union's type for all of them.
     *
     * <p>{@code deleted} rather than {@code deleted_at}: only the boolean is
     * ever read, and the five tables do not agree on whether that column is a
     * timestamp or a timestamp with time zone.
     *
     * <p>A comment carries the ID of whatever it hangs off so the queue can
     * link to it. A comment on a solution points at the solution's problem —
     * a solution has no page of its own, and a link a moderator cannot follow
     * is the same as no link.
     */
    private static final String TARGET_LATERAL = """
            LEFT JOIN LATERAL (
                SELECT problem.title AS title,
                       problem.description AS body,
                       problem.status AS state,
                       problem.deleted_at IS NOT NULL AS deleted,
                       problem.created_at AS created_at,
                       CAST(NULL AS text) AS thumbnail_url,
                       CAST(NULL AS varchar) AS slug,
                       author.id AS author_id,
                       author.full_name AS author_name,
                       author.avatar_url AS author_avatar_url,
                       CAST(NULL AS text) AS parent_type,
                       CAST(NULL AS uuid) AS parent_id
                FROM public.problems problem
                LEFT JOIN public.user_profiles author
                    ON author.id = problem.author_id
                WHERE flag.flaggable_type = 'PROBLEM'
                  AND problem.id = flag.flaggable_id

                UNION ALL

                SELECT showcase.title,
                       showcase.overview,
                       showcase.review_status,
                       showcase.deleted_at IS NOT NULL,
                       showcase.created_at,
                       showcase.cover_image_url,
                       CAST(NULL AS varchar),
                       author.id,
                       author.full_name,
                       author.avatar_url,
                       CAST(NULL AS text),
                       CAST(NULL AS uuid)
                FROM public.showcases showcase
                LEFT JOIN public.user_profiles author
                    ON author.id = showcase.author_id
                WHERE flag.flaggable_type = 'SHOWCASE'
                  AND showcase.id = flag.flaggable_id

                UNION ALL

                SELECT revision.summary,
                       revision.body_markdown,
                       revision.moderation_status,
                       solution.deleted_at IS NOT NULL,
                       solution.created_at,
                       CAST(NULL AS text),
                       CAST(NULL AS varchar),
                       author.id,
                       author.full_name,
                       author.avatar_url,
                       'PROBLEM',
                       solution.problem_id
                FROM public.solutions solution
                LEFT JOIN public.solution_revisions revision
                    ON revision.id = COALESCE(
                           solution.current_published_revision_id,
                           solution.latest_revision_id
                       )
                LEFT JOIN public.user_profiles author
                    ON author.id = solution.author_id
                WHERE flag.flaggable_type = 'SOLUTION'
                  AND solution.id = flag.flaggable_id

                UNION ALL

                SELECT CAST(NULL AS varchar),
                       comment.content,
                       CASE
                           WHEN comment.removed_at IS NOT NULL THEN 'REMOVED'
                           ELSE 'PUBLISHED'
                       END,
                       comment.deleted_at IS NOT NULL,
                       comment.created_at,
                       CAST(NULL AS text),
                       CAST(NULL AS varchar),
                       author.id,
                       author.full_name,
                       author.avatar_url,
                       CASE
                           WHEN comment.commentable_type = 'solution'
                               THEN 'PROBLEM'
                           ELSE UPPER(comment.commentable_type)
                       END,
                       COALESCE(
                           commented_solution.problem_id,
                           comment.commentable_id
                       )
                FROM public.comments comment
                LEFT JOIN public.user_profiles author
                    ON author.id = comment.author_id
                LEFT JOIN public.solutions commented_solution
                    ON comment.commentable_type = 'solution'
                   AND commented_solution.id = comment.commentable_id
                WHERE flag.flaggable_type = 'COMMENT'
                  AND comment.id = flag.flaggable_id

                UNION ALL

                SELECT program.name,
                       program.description,
                       CAST(program.state AS text),
                       program.deleted_at IS NOT NULL,
                       program.created_at,
                       organization.logo_url,
                       program.handle,
                       organization.id,
                       organization.name,
                       organization.logo_url,
                       CAST(NULL AS text),
                       CAST(NULL AS uuid)
                FROM public.programs program
                LEFT JOIN public.organizations organization
                    ON organization.id = program.organization_id
                WHERE flag.flaggable_type = 'PROGRAM'
                  AND program.id = flag.flaggable_id
            ) target ON TRUE
            """;

    /**
     * Every flag ever raised on the same piece of content.
     *
     * <p>Deliberately blind to the filters the caller applied. A queue filtered
     * to {@code PENDING} that also counted only pending flags would tell a
     * moderator "1 report" about a post five people have reported and one
     * colleague has already dismissed for, which is the opposite of the
     * judgement the number exists to inform.
     *
     * <p>The reasons arrive as one comma-joined string rather than an array:
     * the values are a closed enum with no commas in them, and a text column
     * needs no array handling on the way back. The caller splits it.
     */
    private static final String COUNTS_LATERAL = """
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS report_count,
                       COUNT(*) FILTER (
                           WHERE sibling.status = 'PENDING'
                       ) AS pending_count,
                       string_agg(DISTINCT sibling.reason, ',') AS reasons,
                       BOOL_OR(sibling.source = 'AUTOMATED') AS automated,
                       MIN(sibling.created_at) AS first_reported_at,
                       MAX(sibling.created_at) AS last_reported_at,
                       (
                           array_agg(
                               sibling.id ORDER BY sibling.created_at DESC
                           )
                       )[1] AS latest_flag_id
                FROM public.content_flags sibling
                WHERE sibling.flaggable_type = flag.flaggable_type
                  AND sibling.flaggable_id = flag.flaggable_id
            ) counts ON TRUE
            """;

    /**
     * The filters, shared by the listing, its count query and the grouped view
     * so a page can never disagree with its own total.
     *
     * <p>{@code :search} matches the reported writing, the person who wrote it,
     * the person who reported it, and the reporter's own note. It is the one
     * predicate that cannot use an index — the title lives behind a lateral, so
     * every flag has to be resolved before it can be matched. That is a scan of
     * the flag table per search, which an admin-only queue of a few thousand
     * rows can afford and a public endpoint could not.
     */
    private static final String FILTERS = """
            WHERE (
                    CAST(:status AS text) IS NULL
                    OR flag.status = CAST(:status AS text)
                  )
              AND (
                    CAST(:flaggableType AS text) IS NULL
                    OR flag.flaggable_type = CAST(:flaggableType AS text)
                  )
              AND (
                    CAST(:reason AS text) IS NULL
                    OR flag.reason = CAST(:reason AS text)
                  )
              AND (
                    CAST(:reporterId AS uuid) IS NULL
                    OR flag.reporter_id = CAST(:reporterId AS uuid)
                  )
              AND (
                    CAST(:search AS text) IS NULL
                    OR target.title ILIKE CAST(:search AS text)
                    OR target.body ILIKE CAST(:search AS text)
                    OR target.author_name ILIKE CAST(:search AS text)
                    OR reporter.full_name ILIKE CAST(:search AS text)
                    OR flag.description ILIKE CAST(:search AS text)
                  )
            """;

    private static final String FLAG_COLUMNS = """
            SELECT flag.id AS "id",
                   flag.source AS "source",
                   flag.flaggable_type AS "flaggableType",
                   flag.flaggable_id AS "flaggableId",
                   flag.reason AS "reason",
                   flag.description AS "description",
                   flag.status AS "status",
                   flag.reviewed_by AS "reviewedBy",
                   flag.reviewed_at AS "reviewedAt",
                   flag.resolution_note AS "resolutionNote",
                   flag.created_at AS "createdAt",
                   reporter.id AS "reporterId",
                   reporter.full_name AS "reporterName",
                   reporter.avatar_url AS "reporterAvatarUrl",
                   reporter.reputation AS "reporterReputation",
                   target.title AS "targetTitle",
                   target.body AS "targetBody",
                   target.state AS "targetState",
                   target.deleted AS "targetDeleted",
                   target.created_at AS "targetCreatedAt",
                   target.thumbnail_url AS "targetThumbnailUrl",
                   target.slug AS "targetSlug",
                   target.author_id AS "targetAuthorId",
                   target.author_name AS "targetAuthorName",
                   target.author_avatar_url AS "targetAuthorAvatarUrl",
                   target.parent_type AS "targetParentType",
                   target.parent_id AS "targetParentId",
                   counts.report_count AS "reportCount",
                   counts.pending_count AS "pendingCount",
                   counts.reasons AS "reasons",
                   counts.automated AS "automated",
                   counts.first_reported_at AS "firstReportedAt",
                   counts.last_reported_at AS "lastReportedAt",
                   counts.latest_flag_id AS "latestFlagId"
            FROM public.content_flags flag
            LEFT JOIN public.user_profiles reporter
                ON reporter.id = flag.reporter_id
            """;

    /**
     * Ordering by a parameter rather than by {@code Pageable}.
     *
     * <p>Spring appends a sort on {@code Pageable} to the raw statement, which
     * would let a caller order by a column name that only exists inside a
     * lateral — and the two orderings a moderator actually wants, most-reported
     * first and longest-waiting first, are not columns of {@code content_flags}
     * at all. Each branch is a {@code CASE} that collapses to null for every
     * sort but its own, so the unselected keys tie and fall through.
     *
     * <p>The last two keys are the tiebreak. Without a total order a row can
     * appear on two pages, or on none, as soon as two flags share a timestamp.
     */
    private static final String FLAG_ORDER = """
            ORDER BY
                CASE
                    WHEN CAST(:sort AS text) = 'MOST_REPORTED'
                        THEN counts.report_count
                END DESC,
                CASE
                    WHEN CAST(:sort AS text) = 'OLDEST'
                        THEN flag.created_at
                END ASC,
                CASE
                    WHEN CAST(:sort AS text) = 'NEWEST'
                        THEN flag.created_at
                END DESC,
                flag.created_at DESC,
                flag.id DESC
            """;

    /** One page of the queue, each row carrying what was reported. */
    public static final String SEARCH_FLAGS =
            FLAG_COLUMNS + TARGET_LATERAL + COUNTS_LATERAL + FILTERS
                    + FLAG_ORDER;

    /**
     * Its total. The lateral stays in — the search predicate reads through it,
     * and a count that dropped the join would disagree with the page it counts.
     */
    public static final String COUNT_FLAGS = """
            SELECT COUNT(*)
            FROM public.content_flags flag
            LEFT JOIN public.user_profiles reporter
                ON reporter.id = flag.reporter_id
            """ + TARGET_LATERAL + FILTERS;

    /** One flag, resolved exactly as the listing resolves it. */
    public static final String FIND_FLAG = FLAG_COLUMNS + TARGET_LATERAL
            + COUNTS_LATERAL + """
            WHERE flag.id = CAST(:id AS uuid)
            """;

    /**
     * One card per reported thing rather than one per report.
     *
     * <p>Ten people reporting the same spam post is ten rows of identical work
     * in the flat queue, and ten separate decisions to make about one post. The
     * inner query picks which content appears — filters and search apply
     * there — and the counts come from {@link #COUNTS_LATERAL}, which is blind
     * to those filters, so a card always shows the whole story of that target
     * however the queue was narrowed.
     *
     * <p>The derived table is named {@code flag} and keeps the two column names
     * the laterals correlate on, so both fragments are the same text here as in
     * the flat listing.
     */
    public static final String GROUP_FLAGS = """
            SELECT flag.flaggable_type AS "flaggableType",
                   flag.flaggable_id AS "flaggableId",
                   target.title AS "targetTitle",
                   target.body AS "targetBody",
                   target.state AS "targetState",
                   target.deleted AS "targetDeleted",
                   target.created_at AS "targetCreatedAt",
                   target.thumbnail_url AS "targetThumbnailUrl",
                   target.slug AS "targetSlug",
                   target.author_id AS "targetAuthorId",
                   target.author_name AS "targetAuthorName",
                   target.author_avatar_url AS "targetAuthorAvatarUrl",
                   target.parent_type AS "targetParentType",
                   target.parent_id AS "targetParentId",
                   counts.report_count AS "reportCount",
                   counts.pending_count AS "pendingCount",
                   counts.reasons AS "reasons",
                   counts.automated AS "automated",
                   counts.first_reported_at AS "firstReportedAt",
                   counts.last_reported_at AS "lastReportedAt",
                   counts.latest_flag_id AS "latestFlagId"
            FROM (
                SELECT DISTINCT flag.flaggable_type, flag.flaggable_id
                FROM public.content_flags flag
                LEFT JOIN public.user_profiles reporter
                    ON reporter.id = flag.reporter_id
            """ + TARGET_LATERAL + FILTERS + """
            ) flag
            """ + TARGET_LATERAL + COUNTS_LATERAL + """
            ORDER BY
                CASE
                    WHEN CAST(:sort AS text) = 'MOST_REPORTED'
                        THEN counts.report_count
                END DESC,
                CASE
                    WHEN CAST(:sort AS text) = 'OLDEST'
                        THEN counts.first_reported_at
                END ASC,
                CASE
                    WHEN CAST(:sort AS text) = 'NEWEST'
                        THEN counts.last_reported_at
                END DESC,
                counts.last_reported_at DESC,
                flag.flaggable_id DESC
            """;

    /** How many distinct pieces of content the grouped filters match. */
    public static final String COUNT_GROUPS = """
            SELECT COUNT(*)
            FROM (
                SELECT DISTINCT flag.flaggable_type, flag.flaggable_id
                FROM public.content_flags flag
                LEFT JOIN public.user_profiles reporter
                    ON reporter.id = flag.reporter_id
            """ + TARGET_LATERAL + FILTERS + """
            ) grouped
            """;

    /**
     * Every tab badge on the moderation screen, in one statement.
     *
     * <p>Grouping by all three columns at once keeps this to a single round
     * trip and to at most seventy-five rows — three statuses by five reasons by
     * five kinds. Asking for the three breakdowns separately would be three
     * scans of the same table, taken at three different moments, so a flag
     * resolved in between would be counted in one and not the next.
     */
    public static final String TALLY_FLAGS = """
            SELECT flag.status AS "status",
                   flag.reason AS "reason",
                   flag.flaggable_type AS "flaggableType",
                   COUNT(*) AS "total"
            FROM public.content_flags flag
            GROUP BY flag.status, flag.reason, flag.flaggable_type
            """;

    private FlagQueueQueries() {
    }
}
