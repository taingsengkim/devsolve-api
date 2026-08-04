package kh.edu.istad.ite.devsoleapi.feature.program.program_asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProgramAssetRepository
        extends JpaRepository<ProgramAsset, UUID> {

    @Query("""
            select asset
            from ProgramAsset asset
            where asset.program.id in :programIds
              and asset.isInScope = true
            order by asset.createdAt asc
            """)
    List<ProgramAsset> findInScopeByProgramIds(
            @Param("programIds") Collection<UUID> programIds
    );

    List<ProgramAsset> findByProgramIdOrderByCreatedAtAsc(UUID programId);
}
