package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import co.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {

    /**
     * Creates a new comment for a specific report.
     *
     * @param reportId the unique identifier of the report
     * @param request  the payload containing the comment details
     * @return a {@link CommentResponse} representing the created comment
     */
    CommentResponse createReportComment(UUID reportId, CreateCommentRequest request);

    /**
     * Retrieves all comments associated with a specific report.
     *
     * @param reportId the unique identifier of the report
     * @return a list of {@link CommentResponse} representing comments on the report
     */
    List<CommentResponse> findReportComments(UUID reportId);
}


