package kh.edu.istad.ite.devsoleapi.feature.showcase;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.SaveShowcaseDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDraftResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A showcase in progress. Drafts are private to their author — never in a feed,
 * never indexed, never in front of a moderator. Only
 * {@code POST /showcase-drafts/{id}/submit} posts one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/showcase-drafts")
public class ShowcaseDraftController {

    private final ShowcaseDraftService showcaseDraftService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowcaseDraftResponse create(
            @Valid @RequestBody SaveShowcaseDraftRequest request
    ) {
        return showcaseDraftService.create(request);
    }

    /** Newest edit first, which is what a "continue where you left off" list wants. */
    @GetMapping
    public Page<ShowcaseDraftResponse> findMine(
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return showcaseDraftService.findMine(pageable);
    }

    @GetMapping("/{id}")
    public ShowcaseDraftResponse findById(@PathVariable UUID id) {
        return showcaseDraftService.findById(id);
    }

    /**
     * The autosave target. A whole-draft replace rather than a patch, so
     * repeating it is harmless and a field the client stops sending is cleared
     * rather than silently kept.
     */
    @PutMapping("/{id}")
    public ShowcaseDraftResponse save(
            @PathVariable UUID id,
            @Valid @RequestBody SaveShowcaseDraftRequest request
    ) {
        return showcaseDraftService.save(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        showcaseDraftService.delete(id);
    }

    /**
     * Posts the draft and discards it. Returns the showcase so the client can
     * navigate straight to it. A rejected submission leaves the draft as it was.
     */
    @PostMapping("/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowCasesResponse submit(@PathVariable UUID id) {
        return showcaseDraftService.submit(id);
    }
}
