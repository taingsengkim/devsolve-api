package kh.edu.istad.ite.devsoleapi.feature.comment.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CommentCreateRequest {

    @NotBlank(message = "Commentable type is required")
    private String commentableType;

    @NotNull(message = "Commentable ID is required")
    private UUID commentableId;

    private UUID parentCommentId;

    @NotBlank(message = "Content is required")
    private String content;
}