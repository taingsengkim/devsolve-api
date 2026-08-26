package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    /**
     * Soft-deleted problems are counted as well: category_id is NOT NULL and
     * survives the soft delete, so the row still pins the category down.
     */
    long countByCategoryId(UUID categoryId);

    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findActiveById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.id = :id")
    Optional<Problem> findActiveByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select p
            from Problem p
            where p.id = :id
              and p.status in (
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
              )
            """)
    Optional<Problem> findPublicById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from Problem p
            where p.id = :id
              and p.status in (
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
              )
            """)
    Optional<Problem> findPublicByIdForUpdate(@Param("id") UUID id);

    /**
     * The public problem feed.
     *
     * @param queryPattern already lowercased and wildcard-wrapped, or null.
     *                     Searches title and description; the leading wildcard
     *                     needs the trigram indexes in schema.sql to perform.
     * @param status       narrows to one of the public statuses. Null returns
     *                     all three, which is the old behaviour.
     * @param unansweredOnly keeps only problems with no published solution.
     *                     This is the view that makes a problem site work —
     *                     the queue of things somebody could still help with —
     *                     and it has to be a predicate rather than a filter
     *                     applied after paging, or page one comes back
     *                     half empty.
     */
    @Query("""
            select p
            from Problem p
            where p.status in (
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
            )
              and (:status is null or p.status = :status)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:sdlcPhase is null or p.sdlcPhase = :sdlcPhase)
              and (
                  :queryPattern is null
                  or lower(p.title) like :queryPattern
                  or lower(p.description) like :queryPattern
              )
              and (
                  :tagSlug is null
                  or exists (
                      select pt.id
                      from ProblemTag pt
                      where pt.problem = p and pt.tag.slug = :tagSlug
                  )
              )
              and (
                  :technology is null
                  or exists (
                      select tech.id
                      from ProblemTechnology tech
                      where tech.problem = p
                        and lower(tech.name) = :technology
                  )
              )
              and (
                  :unansweredOnly = false
                  or not exists (
                      select solution.id
                      from Solution solution
                      where solution.problem = p
                        and solution.currentPublishedRevision is not null
                        and solution.deletedAt is null
                  )
              )
            """)
    Page<Problem> findPublished(
            @Param("categoryId") UUID categoryId,
            @Param("sdlcPhase") SdlcPhase sdlcPhase,
            @Param("tagSlug") String tagSlug,
            @Param("technology") String technology,
            @Param("queryPattern") String queryPattern,
            @Param("status") ProblemStatus status,
            @Param("unansweredOnly") boolean unansweredOnly,
            Pageable pageable
    );

    /**
     * The same feed ordered by vote score, which a problem row cannot express
     * on its own. See {@code ShowCasesRepository.searchPublishedByScore} for
     * why the score is a scalar subquery rather than an aggregate join.
     *
     * @param since restricts to recently published problems, which is what
     *              separates trending from an all-time leaderboard
     */
    @Query("""
            select p
            from Problem p
            where p.status in (
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
            )
              and (:status is null or p.status = :status)
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:sdlcPhase is null or p.sdlcPhase = :sdlcPhase)
              and (
                  :queryPattern is null
                  or lower(p.title) like :queryPattern
                  or lower(p.description) like :queryPattern
              )
              and (
                  :tagSlug is null
                  or exists (
                      select pt.id
                      from ProblemTag pt
                      where pt.problem = p and pt.tag.slug = :tagSlug
                  )
              )
              and (
                  :technology is null
                  or exists (
                      select tech.id
                      from ProblemTechnology tech
                      where tech.problem = p
                        and lower(tech.name) = :technology
                  )
              )
              and (
                  :unansweredOnly = false
                  or not exists (
                      select solution.id
                      from Solution solution
                      where solution.problem = p
                        and solution.currentPublishedRevision is not null
                        and solution.deletedAt is null
                  )
              )
              and (:since is null or p.publishedAt >= :since)
            order by (
                select coalesce(sum(vote.voteValue), 0)
                from Vote vote
                where vote.votableType = :voteType
                  and vote.votableId = p.id
            ) desc, p.publishedAt desc, p.id desc
            """)
    Page<Problem> findPublishedByScore(
            @Param("categoryId") UUID categoryId,
            @Param("sdlcPhase") SdlcPhase sdlcPhase,
            @Param("tagSlug") String tagSlug,
            @Param("technology") String technology,
            @Param("queryPattern") String queryPattern,
            @Param("status") ProblemStatus status,
            @Param("unansweredOnly") boolean unansweredOnly,
            @Param("since") Instant since,
            @Param("voteType") VoteType voteType,
            Pageable pageable
    );

    /**
     * The "has somebody already asked this?" panel that opens while a user is
     * typing a new problem.
     *
     * <p>This cannot reuse the {@code LIKE} matching of {@link #findPublished}.
     * That one filters — a row either contains the substring or it does not —
     * and a draft title is a paraphrase of the older problem, not a substring
     * of it. Trigram similarity is what survives the rewording and the typos,
     * and it also ranks, which is the whole point of a suggestion list.
     *
     * <p>Title and description are matched by different operators on purpose.
     * {@code similarity()} compares two whole strings and is penalised by a
     * length gap: right for title against title, near useless for a short
     * title against a paragraph. {@code %>} instead scores the needle against
     * the best-matching extent of the haystack, so a title still matches a
     * description that discusses it at length.
     *
     * <p>Neither predicate spells its own cutoff, because a bare
     * {@code similarity(...) > x} cannot use a GIN index and would degrade
     * into a sequential scan over every published problem. The cutoffs come
     * from the session defaults — {@code pg_trgm.similarity_threshold} (0.3)
     * and {@code pg_trgm.word_similarity_threshold} (0.6) — which is what
     * keeps both predicates on the {@code idx_problems_*_trgm} indexes.
     *
     * <p>Solved problems sort first: somebody about to write a question is
     * better served by an answer than by company. Everything returned already
     * cleared the similarity threshold, so this reorders plausible matches
     * rather than promoting irrelevant ones.
     *
     * @param query      already trimmed, lowercased and length-capped by the
     *                   caller; the column side is lowercased here to match
     *                   the indexed expression
     * @param excludeId  the problem being edited, so it cannot suggest itself.
     *                   Null when the draft has not been saved yet.
     */
    @Query(
            value = """
                    SELECT p.id AS id,
                           p.title AS title,
                           p.status AS status,
                           p.view_count AS "viewCount",
                           (
                               SELECT COUNT(*)
                               FROM public.solutions solution
                               WHERE solution.problem_id = p.id
                                 AND solution.current_published_revision_id
                                     IS NOT NULL
                                 AND solution.deleted_at IS NULL
                           ) AS "solutionCount"
                    FROM public.problems p
                    WHERE p.deleted_at IS NULL
                      AND p.status IN ('PUBLISHED', 'RESOLVED', 'CLOSED')
                      AND (
                          CAST(:excludeId AS uuid) IS NULL
                          OR p.id <> CAST(:excludeId AS uuid)
                      )
                      AND (
                          LOWER(p.title) % :query
                          OR LOWER(p.description) %> :query
                      )
                    ORDER BY (p.status = 'RESOLVED') DESC,
                             similarity(LOWER(p.title), :query) * 2
                                 + word_similarity(
                                       :query,
                                       LOWER(p.description)
                                   ) DESC,
                             p.view_count DESC,
                             p.id DESC
                    LIMIT :maxResults
                    """,
            nativeQuery = true
    )
    List<RelatedProblemProjection> findRelated(
            @Param("query") String query,
            @Param("excludeId") UUID excludeId,
            @Param("maxResults") int maxResults
    );

    Page<Problem> findAllByAuthorId(UUID authorId, Pageable pageable);

    Page<Problem> findAllByAuthorIdAndStatusIn(
            UUID authorId,
            Collection<ProblemStatus> statuses,
            Pageable pageable
    );

    Page<Problem> findAllByStatus(
            ProblemStatus status,
            Pageable pageable
    );

    long countByStatus(ProblemStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Problem p
            set p.viewCount = p.viewCount + 1
            where p.id = :id
              and p.status in (
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                  kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
              )
            """)
    int incrementPublicViewCount(@Param("id") UUID id);
}
