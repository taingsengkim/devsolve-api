package co.istad.ite.devsoleapi.feature.comment;


import co.istad.ite.devsoleapi.feature.comment.dto.CommentCreateRequest;
import co.istad.ite.devsoleapi.feature.comment.dto.CommentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> getComments(@RequestParam String type, @RequestParam UUID id) {

        return commentService.getComments(type, id);

    }


    @PostMapping
    public CommentResponse createComment(@Valid @RequestBody CommentCreateRequest request) {

        return commentService.create(request);

    }


    @PatchMapping("/{id}")
    public CommentResponse updateComment(@PathVariable UUID id, @RequestParam String content) {

        return commentService.update(id, content);

    }

    @DeleteMapping("/{id}")
    public void deleteComment(@PathVariable UUID id) {

        commentService.delete(id);


    }

}