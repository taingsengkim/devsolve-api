package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findBySlug(String slug);

    List<Tag> findAllByIdIn(Collection<UUID> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Tag t
            set t.usageCount = t.usageCount + 1
            where t.id in :ids
            """)
    int incrementUsageCounts(@Param("ids") Collection<UUID> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    update tags
                    set usage_count = greatest(usage_count - 1, 0)
                    where id in (:ids)
                    """,
            nativeQuery = true
    )
    int decrementUsageCounts(@Param("ids") Collection<UUID> ids);
}
