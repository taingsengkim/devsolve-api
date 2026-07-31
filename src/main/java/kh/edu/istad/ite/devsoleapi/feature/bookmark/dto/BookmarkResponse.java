package kh.edu.istad.ite.devsoleapi.feature.bookmark.dto;

import java.time.LocalDateTime;

public record BookmarkResponse(
        String id,
        String userId,
        String userFullName,
        String bookmarkableType,
        String bookmarkableId,
        String bookmarkableName,
        LocalDateTime createdAt
) {
}