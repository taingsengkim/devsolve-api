package kh.edu.istad.ite.devsoleapi.feature.problem;

// ProblemRepository.java

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    @Query("SELECT p FROM Problem p WHERE p.deletedAt IS NULL AND p.id = :id")
    Optional<Problem> findActiveById(@Param("id") UUID id);

    @Query("SELECT p FROM Problem p WHERE p.deletedAt IS NULL " +
            "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
            "AND (:status IS NULL OR p.status = :status)")
    Page<Problem> findActiveProblems(@Param("categoryId") UUID categoryId,
                                     @Param("status") ProblemStatus status,
                                     Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Problem p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id AND p.deletedAt IS NULL")
    int incrementViewCount(@Param("id") UUID id);
}