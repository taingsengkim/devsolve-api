package kh.edu.istad.ite.devsoleapi.feature.bookmark.dto;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;

import java.util.UUID;

public record BookmarkStatusResponse(
        BookmarkType bookmarkableType,
        UUID bookmarkableId,
        boolean bookmarked
) {
}
