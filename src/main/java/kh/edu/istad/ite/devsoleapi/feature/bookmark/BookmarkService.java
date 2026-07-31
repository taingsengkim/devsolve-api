package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkRequest;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;

import java.util.List;

public interface BookmarkService {

    // Create a bookmark
    BookmarkResponse bookmark(BookmarkRequest request);

    // Remove a bookmark
    void unbookmark(String userId, String bookmarkableType, String bookmarkableId);

    // Get all bookmarks by user
    List<BookmarkResponse> getUserBookmarks(String userId);

    // Get all users who bookmarked an entity
    List<BookmarkResponse> getBookmarkers(String bookmarkableType, String bookmarkableId);

    // Check if user bookmarked
    boolean isBookmarked(String userId, String bookmarkableType, String bookmarkableId);

    // Count bookmarks by user
    long countUserBookmarks(String userId);

    // Count bookmarks for an entity
    long countBookmarks(String bookmarkableType, String bookmarkableId);
}