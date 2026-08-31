package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import org.springframework.data.domain.Pageable;
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

    /**
     * Matching runs on the slug rather than the name so the caller's query can
     * be normalized the same way the stored slug was — "Node.js", "node js" and
     * "NODEJS" all find {@code node-js}. It also keeps the pattern
     * {@code [a-z0-9-]}, with no user-supplied {@code %} or {@code _} to escape.
     *
     * <p>An empty query is the unfiltered listing, ordered by usage.
     */
    @Query("""
            select t from Tag t
            where t.slug like concat('%', :query, '%')
            order by
                case when t.slug like concat(:query, '%') then 0 else 1 end,
                t.usageCount desc,
                t.name asc
            """)
    List<Tag> search(@Param("query") String query, Pageable pageable);

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
