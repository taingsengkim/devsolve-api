package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagDeletionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin maintenance of the shared tag catalogue. Restricted by the
 * {@code /api/v1/admin/**} rule in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
public class TagAdminController {

    private final TagAdminService tagAdminService;

    /**
     * Deletes a tag. Refused with 409 while it is still on any content unless
     * {@code force=true}, which unlinks it everywhere first.
     */
    @DeleteMapping("/{id}")
    public TagDeletionResponse delete(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return tagAdminService.delete(id, force);
    }
}
