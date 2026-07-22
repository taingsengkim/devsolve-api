package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;

import java.util.List;
import java.util.UUID;

public interface CommentService {

    /**
     * Retrieves all comments associated with a specific report.
     *
     * @param reportId the unique identifier of the report
     * @return a list of {@link CommentResponse} representing comments on the report
     */
    List<CommentResponse> findReportComments(UUID reportId);
}

