package co.istad.ite.devsoleapi.feature.comments;

import co.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/reports/{id}/comments")
    public List<CommentResponse> findReportComments(@PathVariable UUID id) {
        return commentService.findReportComments(id);
    }
}

