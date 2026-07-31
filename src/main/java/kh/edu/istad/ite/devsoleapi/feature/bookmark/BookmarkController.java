package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkRequest;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<BookmarkResponse> bookmark(@Valid @RequestBody BookmarkRequest request) {
        BookmarkResponse response = bookmarkService.bookmark(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookmarkResponse>> getUserBookmarks(@PathVariable String userId) {
        List<BookmarkResponse> bookmarks = bookmarkService.getUserBookmarks(userId);
        return ResponseEntity.ok(bookmarks);
    }


    @GetMapping("/bookmarkers")
    public ResponseEntity<List<BookmarkResponse>> getBookmarkers(
            @RequestParam String bookmarkableType,
            @RequestParam String bookmarkableId) {
        List<BookmarkResponse> bookmarkers = bookmarkService.getBookmarkers(bookmarkableType, bookmarkableId);
        return ResponseEntity.ok(bookmarkers);
    }


    @GetMapping("/check")
    public ResponseEntity<Boolean> isBookmarked(
            @RequestParam String userId,
            @RequestParam String bookmarkableType,
            @RequestParam String bookmarkableId) {
        boolean isBookmarked = bookmarkService.isBookmarked(userId, bookmarkableType, bookmarkableId);
        return ResponseEntity.ok(isBookmarked);
    }


    @GetMapping("/count/user/{userId}")
    public ResponseEntity<Long> countUserBookmarks(@PathVariable String userId) {
        long count = bookmarkService.countUserBookmarks(userId);
        return ResponseEntity.ok(count);
    }


    @GetMapping("/count")
    public ResponseEntity<Long> countBookmarks(
            @RequestParam String bookmarkableType,
            @RequestParam String bookmarkableId) {
        long count = bookmarkService.countBookmarks(bookmarkableType, bookmarkableId);
        return ResponseEntity.ok(count);
    }

    @DeleteMapping("/unbookmark")
    public ResponseEntity<Void> unbookmark(
            @RequestParam String userId,
            @RequestParam String bookmarkableType,
            @RequestParam String bookmarkableId) {
        bookmarkService.unbookmark(userId, bookmarkableType, bookmarkableId);
        return ResponseEntity.noContent().build();
    }
}