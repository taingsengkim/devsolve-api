package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowcaseTagRepository
        extends JpaRepository<ShowcaseTag, ShowcaseTagId> {

    @EntityGraph(attributePaths = "tag")
    @Query("select st from ShowcaseTag st where st.showcase.id = :showcaseId")
    List<ShowcaseTag> findAllByShowcaseId(
            @Param("showcaseId") UUID showcaseId
    );

    @EntityGraph(attributePaths = "tag")
    @Query("""
            select st from ShowcaseTag st
            where st.showcase.id in :showcaseIds
            """)
    List<ShowcaseTag> findAllByShowcaseIdIn(
            @Param("showcaseIds") Collection<UUID> showcaseIds
    );

    @Modifying
    @Query("delete from ShowcaseTag st where st.showcase.id = :showcaseId")
    void deleteAllByShowcaseId(@Param("showcaseId") UUID showcaseId);

    @Query("select count(st) from ShowcaseTag st where st.tag.id = :tagId")
    long countByTagId(@Param("tagId") UUID tagId);

    @Modifying
    @Query("delete from ShowcaseTag st where st.tag.id = :tagId")
    int deleteAllByTagId(@Param("tagId") UUID tagId);
}
