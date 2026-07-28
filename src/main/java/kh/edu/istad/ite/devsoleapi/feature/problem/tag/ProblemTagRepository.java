package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProblemTagRepository
        extends JpaRepository<ProblemTag, ProblemTagId> {

    @EntityGraph(attributePaths = "tag")
    @Query("select pt from ProblemTag pt where pt.problem.id = :problemId")
    List<ProblemTag> findAllByProblemId(@Param("problemId") UUID problemId);

    @Query("delete from ProblemTag pt where pt.problem.id = :problemId")
    @org.springframework.data.jpa.repository.Modifying
    void deleteAllByProblemId(@Param("problemId") UUID problemId);
}
