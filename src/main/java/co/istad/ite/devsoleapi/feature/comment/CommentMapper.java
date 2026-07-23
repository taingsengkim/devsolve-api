package co.istad.ite.devsoleapi.feature.comment;


import co.istad.ite.devsoleapi.feature.comment.dto.CommentResponse;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment) {

        return CommentResponse.builder()
                .id(comment.getId())
                .commentableType(comment.getCommentableType().getValue())
                .commentableId(comment.getCommentableId())
                .parentCommentId(comment.getParentCommentId())
                .authorId(comment.getAuthorId())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

}