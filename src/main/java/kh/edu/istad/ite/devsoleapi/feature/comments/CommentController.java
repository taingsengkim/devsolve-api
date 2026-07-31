package kh.edu.istad.ite.devsoleapi.feature.comments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.UpdateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/comments")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.create(request);
    }

    @GetMapping
    public Page<CommentResponse> findByTarget(
            @RequestParam CommentableType commentableType,
            @RequestParam UUID commentableId,
            @RequestParam(required = false) UUID parentCommentId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return commentService.findByTarget(
                commentableType,
                commentableId,
                parentCommentId,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/mine")
    public Page<CommentResponse> findMine(
            @RequestParam(required = false)
            CommentableType commentableType,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be at least 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize
    ) {
        return commentService.findMine(
                commentableType,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{id}")
    public CommentResponse findById(@PathVariable UUID id) {
        return commentService.findById(id);
    }

    @PatchMapping("/{id}")
    public CommentResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCommentRequest request
    ) {
        return commentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        commentService.delete(id);
    }
}


