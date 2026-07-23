package co.istad.ite.devsoleapi.feature.comment.dto;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CommentResponse {

    private UUID id;

    private String commentableType;

    private UUID commentableId;

    private UUID parentCommentId;

    private UUID authorId;

    private String authorName;

    private String content;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}