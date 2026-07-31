package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PutMapping("/{type}/{targetId}")
    public BookmarkResponse bookmark(
            @PathVariable BookmarkType type,
            @PathVariable UUID targetId
    ) {
        return bookmarkService.bookmark(type, targetId);
    }

    @DeleteMapping("/{type}/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbookmark(
            @PathVariable BookmarkType type,
            @PathVariable UUID targetId
    ) {
        bookmarkService.unbookmark(type, targetId);
    }

    @GetMapping("/{type}/{targetId}/status")
    public BookmarkStatusResponse getStatus(
            @PathVariable BookmarkType type,
            @PathVariable UUID targetId
    ) {
        return bookmarkService.getStatus(type, targetId);
    }

    @GetMapping("/mine")
    public Page<BookmarkResponse> getMine(
            @RequestParam(required = false) BookmarkType type,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return bookmarkService.getMine(type, pageNumber, pageSize);
    }
}
