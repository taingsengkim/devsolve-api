package kh.edu.istad.ite.devsoleapi.feature.comments.dto;

import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CommentResponse {

    private UUID id;

    private CommentableType commentableType;

    private UUID commentableId;

    private UUID parentCommentId;

    private UUID authorId;

    private String content;

    private LocalDateTime createdAt;


    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
