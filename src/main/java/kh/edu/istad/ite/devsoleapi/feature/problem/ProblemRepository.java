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
