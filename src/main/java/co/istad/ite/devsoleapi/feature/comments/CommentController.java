package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import co.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/reports/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createReportComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.createReportComment(id, request);
    }

    @GetMapping("/reports/{id}/comments")
    public List<CommentResponse> findReportComments(@PathVariable UUID id) {
        return commentService.findReportComments(id);
    }
}


