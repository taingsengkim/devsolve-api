package co.istad.ite.devsoleapi.feature.comment;


import co.istad.ite.devsoleapi.feature.comment.dto.CommentCreateRequest;
import co.istad.ite.devsoleapi.feature.comment.dto.CommentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    @Override
    public CommentResponse create(CommentCreateRequest request) {

        Comment comment = Comment.builder()
                .commentableType(CommentableType.fromValue(request.getCommentableType()))
                .commentableId(request.getCommentableId())
                .parentCommentId(request.getParentCommentId())
                // TODO: Replace with current logged-in user's ID from Keycloak
                .authorId(UUID.randomUUID())
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);

        return commentMapper.toResponse(saved);
    }

    @Override
    public List<CommentResponse> getComments(String commentableType, UUID commentableId) {

        return commentRepository
                .findByCommentableTypeAndCommentableIdAndDeletedAtIsNull(
                        CommentableType.fromValue(commentableType),
                        commentableId
                )
                .stream()
                .map(commentMapper::toResponse)
                .toList();
    }

    @Override
    public CommentResponse update(UUID commentId, String content) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        comment.setContent(content);

        Comment updated = commentRepository.save(comment);

        return commentMapper.toResponse(updated);
    }

    @Override
    public void delete(UUID commentId) {

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        comment.setDeletedAt(LocalDateTime.now());

        commentRepository.save(comment);
    }
}