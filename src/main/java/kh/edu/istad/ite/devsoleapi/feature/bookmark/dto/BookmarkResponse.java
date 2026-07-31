package kh.edu.istad.ite.devsoleapi.feature.bookmark.dto;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookmarkResponse(
        UUID id,
        BookmarkType bookmarkableType,
        UUID bookmarkableId,
        boolean available,
        String targetTitle,
        String targetPreview,
        String targetImageUrl,
        LocalDateTime createdAt
) {
}
