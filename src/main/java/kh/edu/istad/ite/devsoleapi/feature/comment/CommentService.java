package kh.edu.istad.ite.devsoleapi.feature.comment;

import kh.edu.istad.ite.devsoleapi.feature.comment.dto.CommentCreateRequest;
import kh.edu.istad.ite.devsoleapi.feature.comment.dto.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {
    /**
     * Create a new comment for a problem or solution
     *
     * @param request comment information from user
     * @return create comment response
     */

    CommentResponse create(CommentCreateRequest request);

    /**
     * Get all comments by conten type and ID
     * @param commentableType type of conten (PROBLEM ,SOLUTION)
     * @param commentableId ID of the content
     * @return list of comment
     */

    List<CommentResponse> getComments(String commentableType, UUID commentableId);

    /**
     * Update user's own comment content.
     * @param commentId ID of comment
     * @param content new comment content
     * @return update comment response
     */

    CommentResponse update(UUID commentId, String content);

    /**
     * Soft delete user's own comment
     * @param commentId commentId of comment
     */

    void delete(UUID commentId);



}
