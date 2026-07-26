package kh.edu.istad.ite.devsoleapi.feature.program.program_update;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProgramUpdateRepository extends JpaRepository<ProgramUpdate, UUID> {

    Page<ProgramUpdate> findByProgramId(UUID programId, Pageable pageable);
}
