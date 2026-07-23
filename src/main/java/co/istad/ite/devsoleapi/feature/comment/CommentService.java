package co.istad.ite.devsoleapi.feature.comment;

import co.istad.ite.devsoleapi.feature.comment.dto.CommentCreateRequest;
import co.istad.ite.devsoleapi.feature.comment.dto.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {

    CommentResponse create(CommentCreateRequest request);

    List<CommentResponse> getComments(
            String commentableType,
            UUID commentableId
    );

    CommentResponse update(
            UUID commentId,
            String content
    );

    void delete(UUID commentId);
}
