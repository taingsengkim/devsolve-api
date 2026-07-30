package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentMapper;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final ReportService reportService;
    private final CommentMapper commentMapper;

    @Override
    public CommentResponse createReportComment(UUID reportId, CreateCommentRequest request) {
        reportService.requireViewAccess(reportId);

        String userIdStr = AuthUtils.extractUserId();
        UUID authorId = UUID.fromString(userIdStr);

        Comment comment = commentMapper.toEntity(request);
        comment.setAuthorId(authorId);
        comment.setCommentableType(CommentableType.REPORT);
        comment.setCommentableId(reportId);

        if (request.getParentCommentId() != null) {
            Comment parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Parent comment not found"
                            )
                    );
            comment.setParentComment(parentComment);
        }

        Comment savedComment = commentRepository.save(comment);
        return commentMapper.toResponse(savedComment);
    }

    @Override
    public List<CommentResponse> findReportComments(UUID reportId) {

        reportService.requireViewAccess(reportId);

        List<Comment> comments = commentRepository.findByCommentableTypeAndCommentableId(
                CommentableType.REPORT,
                reportId
        );

        return commentMapper.toResponse(comments);
    }
}

