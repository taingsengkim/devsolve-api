package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProblemTechnologyRepository
        extends JpaRepository<ProblemTechnology, UUID> {

    List<ProblemTechnology> findAllByProblemIdOrderByNameAsc(UUID problemId);

    List<ProblemTechnology> findAllByProblemIdInOrderByNameAsc(
            Collection<UUID> problemIds
    );

    void deleteAllByProblemId(UUID problemId);
}
