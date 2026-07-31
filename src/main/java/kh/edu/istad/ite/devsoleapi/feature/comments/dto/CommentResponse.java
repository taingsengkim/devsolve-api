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

    private String authorName;

    private String authorAvatarUrl;

    private String content;

    private boolean internal;

    private long replyCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
