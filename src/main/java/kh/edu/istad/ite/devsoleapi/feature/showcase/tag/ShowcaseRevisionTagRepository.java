package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowcaseRevisionTagRepository
        extends JpaRepository<ShowcaseRevisionTag, ShowcaseRevisionTagId> {

    @EntityGraph(attributePaths = "tag")
    @Query("""
            select srt from ShowcaseRevisionTag srt
            where srt.revision.id = :revisionId
            """)
    List<ShowcaseRevisionTag> findAllByRevisionId(
            @Param("revisionId") UUID revisionId
    );

    @EntityGraph(attributePaths = "tag")
    @Query("""
            select srt from ShowcaseRevisionTag srt
            where srt.revision.id in :revisionIds
            """)
    List<ShowcaseRevisionTag> findAllByRevisionIdIn(
            @Param("revisionIds") Collection<UUID> revisionIds
    );

    @Modifying
    @Query("""
            delete from ShowcaseRevisionTag srt
            where srt.revision.id = :revisionId
            """)
    void deleteAllByRevisionId(@Param("revisionId") UUID revisionId);

    @Query("""
            select count(srt) from ShowcaseRevisionTag srt
            where srt.tag.id = :tagId
            """)
    long countByTagId(@Param("tagId") UUID tagId);

    @Modifying
    @Query("delete from ShowcaseRevisionTag srt where srt.tag.id = :tagId")
    int deleteAllByTagId(@Param("tagId") UUID tagId);
}
