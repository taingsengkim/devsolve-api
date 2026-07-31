package kh.edu.istad.ite.devsoleapi.feature.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookmarkRequest(
        @NotBlank(message = "User ID is required")
        String userId,

        @NotBlank(message = "Bookmarkable type is required")
        String bookmarkableType,

        @NotBlank(message = "Bookmarkable ID is required")
        String bookmarkableId
) {
}