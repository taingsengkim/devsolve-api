package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentThreadResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentSort;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface CommentService {

    CommentResponse create(CreateCommentRequest request);

    Page<CommentResponse> findByTarget(
            CommentableType commentableType,
            UUID commentableId,
            UUID parentCommentId,
            CommentSort sort,
            int pageNumber,
            int pageSize
    );

    /**
     * Root comments with the start of each reply thread already attached, so
     * the first screen of a discussion is one request instead of one per
     * visible comment.
     */
    Page<CommentThreadResponse> findThread(
            CommentableType commentableType,
            UUID commentableId,
            CommentSort sort,
            int replyLimit,
            int pageNumber,
            int pageSize
    );

    CommentResponse findById(UUID id);

    Page<CommentResponse> findMine(
            CommentableType commentableType,
            int pageNumber,
            int pageSize
    );

    CommentResponse update(UUID id, UpdateCommentRequest request);

    void delete(UUID id);

    /**
     * Takes a comment's text down on a moderator's decision, leaving the row
     * so the thread underneath it survives and the removal is visible rather
     * than silent. Called by the moderation feature when a flag is upheld;
     * the caller is responsible for having checked the moderator's authority.
     */
    void removeByModerator(UUID commentId, UUID moderatorId);
}
