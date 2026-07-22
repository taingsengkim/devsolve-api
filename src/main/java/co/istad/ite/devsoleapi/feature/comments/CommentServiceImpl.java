package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.dto.CommentMapper;
import co.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import co.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import co.istad.ite.devsoleapi.feature.reports.ReportRepository;
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
    private final ReportRepository reportRepository;
    private final CommentMapper commentMapper;

    @Override
    public List<CommentResponse> findReportComments(UUID reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Report not found"
            );
        }

        List<Comment> comments = commentRepository.findByCommentableTypeAndCommentableId(
                CommentableType.REPORT,
                reportId
        );

        return commentMapper.toResponse(comments);
    }
}

