package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, String> {

    // Find specific bookmark
    Optional<Bookmark> findByUserIdAndBookmarkableTypeAndBookmarkableId(
            String userId,
            String bookmarkableType,
            String bookmarkableId
    );

    // Check if bookmark exists
    boolean existsByUserIdAndBookmarkableTypeAndBookmarkableId(
            String userId,
            String bookmarkableType,
            String bookmarkableId
    );

    // Get all bookmarks by user
    List<Bookmark> findByUserId(String userId);

    // Get all bookmarks by bookmarkable
    List<Bookmark> findByBookmarkableTypeAndBookmarkableId(
            String bookmarkableType,
            String bookmarkableId
    );

    // Count bookmarks by user
    long countByUserId(String userId);

    // Count bookmarks by bookmarkable
    long countByBookmarkableTypeAndBookmarkableId(
            String bookmarkableType,
            String bookmarkableId
    );

    // Delete all bookmarks by user
    void deleteByUserId(String userId);

    // Custom query with pagination
    @Query("SELECT b FROM Bookmark b WHERE b.user.id = :userId ORDER BY b.createdAt DESC")
    List<Bookmark> findRecentBookmarksByUser(@Param("userId") String userId);
}