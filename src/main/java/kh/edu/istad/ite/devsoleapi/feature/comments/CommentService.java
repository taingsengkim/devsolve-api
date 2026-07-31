package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface CommentService {

    CommentResponse create(CreateCommentRequest request);

    Page<CommentResponse> findByTarget(
            CommentableType commentableType,
            UUID commentableId,
            UUID parentCommentId,
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
}


