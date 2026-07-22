package co.istad.ite.devsoleapi.feature.comments.dto;

import co.istad.ite.devsoleapi.feature.comments.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CommentMapper {

    @Mapping(target = "parentCommentId", source = "parentComment.id")
    CommentResponse toResponse(Comment comment);



    List<CommentResponse> toResponse(List<Comment> comments);
}
