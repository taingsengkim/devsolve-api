package kh.edu.istad.ite.devsoleapi.feature.showcase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShowCasesRepository extends JpaRepository<ShowCases, UUID> {


    Page<ShowCases> findByReviewStatusAndDeletedAtIsNull(
            ReviewStatus reviewStatus,
            Pageable pageable
    );

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
                    :query IS NULL
                    OR LOWER(showcase.title)
                        LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(showcase.overview)
                        LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(showcase.author.fullName)
                        LIKE LOWER(CONCAT('%', :query, '%'))
              )
            """)
    Page<ShowCases> searchPublished(
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("query") String query,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );

    Page<ShowCases> findByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
            UUID authorId,
            ReviewStatus reviewStatus,
            Pageable pageable
    );

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
