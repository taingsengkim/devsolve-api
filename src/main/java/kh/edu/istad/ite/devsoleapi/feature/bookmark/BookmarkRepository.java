package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository
        extends JpaRepository<Bookmark, UUID> {

    Optional<Bookmark>
    findByUser_IdAndBookmarkableTypeAndBookmarkableId(
            UUID userId,
            BookmarkType bookmarkableType,
            UUID bookmarkableId
    );

    boolean existsByUser_IdAndBookmarkableTypeAndBookmarkableId(
            UUID userId,
            BookmarkType bookmarkableType,
            UUID bookmarkableId
    );

    long deleteByUser_IdAndBookmarkableTypeAndBookmarkableId(
            UUID userId,
            BookmarkType bookmarkableType,
            UUID bookmarkableId
    );

    @Query("""
            select bookmark
            from Bookmark bookmark
            where bookmark.user.id = :userId
              and (
                    :bookmarkableType is null
                    or bookmark.bookmarkableType = :bookmarkableType
              )
            """)
    Page<Bookmark> findMine(
            @Param("userId") UUID userId,
            @Param("bookmarkableType") BookmarkType bookmarkableType,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO public.bookmarks (
                id,
                user_id,
                bookmarkable_type,
                bookmarkable_id,
                created_at
            )
            VALUES (
                :id,
                :userId,
                :bookmarkableType,
                :bookmarkableId,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (
                user_id,
                bookmarkable_type,
                bookmarkable_id
            ) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("bookmarkableType") String bookmarkableType,
            @Param("bookmarkableId") UUID bookmarkableId
    );
}
