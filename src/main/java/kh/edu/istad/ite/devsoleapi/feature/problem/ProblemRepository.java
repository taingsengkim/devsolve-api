package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

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

    @Query("""
            select p
            from Problem p
            where p.status in (
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.PUBLISHED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.RESOLVED,
                kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus.CLOSED
            )
              and (:categoryId is null or p.categoryId = :categoryId)
              and (:sdlcPhase is null or p.sdlcPhase = :sdlcPhase)
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
            """)
    Page<Problem> findPublished(
            @Param("categoryId") UUID categoryId,
            @Param("sdlcPhase") SdlcPhase sdlcPhase,
            @Param("tagSlug") String tagSlug,
            @Param("technology") String technology,
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
