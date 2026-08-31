package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The tag catalogue, shared by problems and showcases. Public and read-only:
 * these tags are already visible on every public problem and showcase, and tag
 * creation happens through those resources rather than here.
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * Suggestions for a tag picker: prefix matches first, then most-used first.
     * No {@code q} lists the most-used tags.
     */
    @GetMapping
    public ResponseEntity<List<TagResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(tagService.search(q, limit));
    }
}
