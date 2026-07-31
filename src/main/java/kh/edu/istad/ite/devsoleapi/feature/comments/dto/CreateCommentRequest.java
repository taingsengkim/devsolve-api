package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;

import java.util.UUID;

public record CreateCommentRequest(
        @NotNull(message = "Commentable type is required")
        CommentableType commentableType,

        @NotNull(message = "Commentable ID is required")
        UUID commentableId,

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must not exceed 5000 characters")
        String content,

        UUID parentCommentId,

        boolean internal
) {
}
