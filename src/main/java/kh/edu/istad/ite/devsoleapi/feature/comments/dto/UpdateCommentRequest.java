package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCommentRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content must not exceed 5000 characters")
        String content
) {
}
