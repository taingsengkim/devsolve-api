package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ShowCasesRepository extends JpaRepository<ShowCases, UUID> {

    /**
     * Counts soft-deleted showcases too. They still hold the category id, so
     * the foreign key still refuses the delete — a count that skipped them
     * would promise something the database will not honour.
     */
    long countByCategory_Id(UUID categoryId);

    /**
     * The public listing: every filter optional, so one query serves the whole
     * feed instead of a branch per combination.
     *
     * <p>{@code author} and {@code category} are eager {@code @ManyToOne}s and
     * the mapper reads a field from each, so without the entity graph a page
     * of twenty costs a secondary select per row. The graph turns those into
     * joins on the query that was already running.
     *
     * @param queryPattern already lowercased and wrapped in wildcards, or null
     *                     for no text filter. The leading wildcard means this
     *                     only performs because of the trigram indexes in
     *                     schema.sql; a plain B-tree cannot serve it.
     */
    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            SELECT showcase
            FROM ShowCases showcase
            WHERE showcase.reviewStatus = :reviewStatus
              AND showcase.deletedAt IS NULL
              AND (
                    :categoryId IS NULL
                    OR showcase.category.id = :categoryId
              )
              AND (
                    :queryPattern IS NULL
                    OR LOWER(showcase.title) LIKE :queryPattern
                    OR LOWER(showcase.overview) LIKE :queryPattern
                    OR LOWER(showcase.author.fullName) LIKE :queryPattern
              )
              AND (
                    :tagSlug IS NULL
                    OR EXISTS (
                        SELECT showcaseTag.id
                        FROM ShowcaseTag showcaseTag
                        WHERE showcaseTag.showcase = showcase
                          AND showcaseTag.tag.slug = :tagSlug
                    )
              )
            """)
    Page<ShowCases> searchPublished(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("queryPattern") String queryPattern,
            @Param("categoryId") UUID categoryId,
            @Param("tagSlug") String tagSlug,
            Pageable pageable
    );

    /**
     * The same listing ordered by vote score, which the showcase row cannot
     * express on its own because scores live in the votes table.
     *
     * <p>The score is a scalar subquery in the ORDER BY rather than an
     * aggregate join: an aggregate would need a GROUP BY that fights the
     * entity graph's fetch joins, and a page of this size does not justify
     * the fight. It costs one index lookup per candidate row against
     * idx_votes_votable.
     *
     * @param since when set, restricts to showcases published after it —
     *              "best this month" rather than "best ever", which is what
     *              keeps a trending feed from freezing solid around whatever
     *              won in its first week.
     */
    @EntityGraph(attributePaths = {"author", "category"})
    @Query("""
            SELECT showcase
            FROM ShowCases showcase
            WHERE showcase.reviewStatus = :reviewStatus
              AND showcase.deletedAt IS NULL
              AND (
                    :categoryId IS NULL
                    OR showcase.category.id = :categoryId
              )
              AND (
                    :queryPattern IS NULL
                    OR LOWER(showcase.title) LIKE :queryPattern
                    OR LOWER(showcase.overview) LIKE :queryPattern
                    OR LOWER(showcase.author.fullName) LIKE :queryPattern
              )
              AND (
                    :tagSlug IS NULL
                    OR EXISTS (
                        SELECT showcaseTag.id
                        FROM ShowcaseTag showcaseTag
                        WHERE showcaseTag.showcase = showcase
                          AND showcaseTag.tag.slug = :tagSlug
                    )
              )
              AND (:since IS NULL OR showcase.createdAt >= :since)
            ORDER BY (
                SELECT COALESCE(SUM(vote.voteValue), 0)
                FROM Vote vote
                WHERE vote.votableType = :voteType
                  AND vote.votableId = showcase.id
            ) DESC, showcase.createdAt DESC, showcase.id DESC
            """)
    Page<ShowCases> searchPublishedByScore(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("queryPattern") String queryPattern,
            @Param("categoryId") UUID categoryId,
            @Param("tagSlug") String tagSlug,
            @Param("since") LocalDateTime since,
            @Param("voteType") VoteType voteType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "category"})
    Page<ShowCases> findByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
            UUID authorId,
            ReviewStatus reviewStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "category"})
    Page<ShowCases> findByAuthor_IdAndDeletedAtIsNull(
            UUID authorId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT *
                    FROM (
                        SELECT
                            showcase.id AS "showcaseId",
                            CAST(NULL AS UUID) AS "revisionId",
                            'INITIAL' AS "submissionType",
                            showcase.author_id AS "authorId",
                            author.full_name AS "authorName",
                            showcase.category_id AS "categoryId",
                            category.name AS "categoryName",
                            showcase.title AS "title",
                            showcase.overview AS "overview",
                            showcase.cover_image_url AS "coverImageUrl",
                            showcase.live_url AS "liveUrl",
                            showcase.repo_url AS "repoUrl",
                            showcase.video_url AS "videoUrl",
                            CAST(showcase.review_status AS TEXT)
                                AS "reviewStatus",
                            showcase.updated_at AS "submittedAt"
                        FROM public.showcases showcase
                        JOIN public.user_profiles author
                          ON author.id = showcase.author_id
                        LEFT JOIN public.categories category
                          ON category.id = showcase.category_id
                        WHERE UPPER(REPLACE(
                                  CAST(showcase.review_status AS TEXT),
                                  '_approval',
                                  ''
                              )) = :reviewStatus
                          AND showcase.deleted_at IS NULL

                        UNION ALL

                        SELECT
                            showcase.id AS "showcaseId",
                            revision.id AS "revisionId",
                            'REVISION' AS "submissionType",
                            showcase.author_id AS "authorId",
                            author.full_name AS "authorName",
                            revision.category_id AS "categoryId",
                            category.name AS "categoryName",
                            revision.title AS "title",
                            revision.overview AS "overview",
                            revision.cover_image_url AS "coverImageUrl",
                            revision.live_url AS "liveUrl",
                            revision.repo_url AS "repoUrl",
                            revision.video_url AS "videoUrl",
                            CAST(revision.review_status AS TEXT)
                                AS "reviewStatus",
                            revision.updated_at AS "submittedAt"
                        FROM public.showcase_revisions revision
                        JOIN public.showcases showcase
                          ON showcase.id = revision.showcase_id
                        JOIN public.user_profiles author
                          ON author.id = showcase.author_id
                        LEFT JOIN public.categories category
                          ON category.id = revision.category_id
                        WHERE UPPER(REPLACE(
                                  CAST(revision.review_status AS TEXT),
                                  '_approval',
                                  ''
                              )) = :reviewStatus
                          AND showcase.deleted_at IS NULL
                    ) review_queue
                    ORDER BY "submittedAt" DESC
                    """,
            countQuery = """
                    SELECT
                        (
                            SELECT COUNT(*)
                            FROM public.showcases showcase
                            WHERE UPPER(REPLACE(
                                      CAST(showcase.review_status AS TEXT),
                                      '_approval',
                                      ''
                                  )) = :reviewStatus
                              AND showcase.deleted_at IS NULL
                        )
                        +
                        (
                            SELECT COUNT(*)
                            FROM public.showcase_revisions revision
                            JOIN public.showcases showcase
                              ON showcase.id = revision.showcase_id
                            WHERE UPPER(REPLACE(
                                      CAST(revision.review_status AS TEXT),
                                      '_approval',
                                      ''
                                  )) = :reviewStatus
                              AND showcase.deleted_at IS NULL
                        )
                    """,
            nativeQuery = true
    )
    Page<ShowcaseReviewQueueProjection> findReviewQueue(
            @Param("reviewStatus") String reviewStatus,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        (
                            SELECT COUNT(*)
                            FROM public.showcases showcase
                            WHERE UPPER(REPLACE(
                                      CAST(showcase.review_status AS TEXT),
                                      '_approval',
                                      ''
                                  )) = :reviewStatus
                              AND showcase.deleted_at IS NULL
                        )
                        +
                        (
                            SELECT COUNT(*)
                            FROM public.showcase_revisions revision
                            JOIN public.showcases showcase
                              ON showcase.id = revision.showcase_id
                            WHERE UPPER(REPLACE(
                                      CAST(revision.review_status AS TEXT),
                                      '_approval',
                                      ''
                                  )) = :reviewStatus
                              AND showcase.deleted_at IS NULL
                        )
                    """,
            nativeQuery = true
    )
    long countReviewQueue(@Param("reviewStatus") String reviewStatus);

    @EntityGraph(attributePaths = {"author", "category"})
    Optional<ShowCases> findByIdAndDeletedAtIsNull(
            UUID id
    );

    Optional<ShowCases> findByIdAndReviewStatusAndDeletedAtIsNull(
            UUID id,
            ReviewStatus reviewStatus
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            UPDATE ShowCases showcase
            SET showcase.viewCount =
                COALESCE(showcase.viewCount, 0) + 1
            WHERE showcase.id = :id
              AND showcase.reviewStatus = :reviewStatus
              AND showcase.deletedAt IS NULL
            """)
    int incrementViewCount(
            @Param("id") UUID id,
            @Param("reviewStatus") ReviewStatus reviewStatus
    );

    @Query("""
            SELECT COALESCE(showcase.viewCount, 0)
            FROM ShowCases showcase
            WHERE showcase.id = :id
            """)
    Integer findViewCountById(@Param("id") UUID id);

    boolean existsByAuthor_IdAndTitleAndDeletedAtIsNull(
            UUID authorId,
            String title
    );

    boolean existsByAuthor_IdAndTitleAndIdNotAndDeletedAtIsNull(
            UUID authorId,
            String title,
            UUID id
    );

    boolean existsByRepoUrl(
            String repoUrl
    );

    boolean existsByLiveUrl(
            String liveUrl
    );
}
