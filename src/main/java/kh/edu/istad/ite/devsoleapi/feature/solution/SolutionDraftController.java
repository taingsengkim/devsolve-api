package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SaveSolutionDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * An answer in progress. Drafts are private to their author — never counted on
 * the problem, never shown to the asker, never in review. Only
 * {@code POST /solution-drafts/{id}/submit} posts one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SolutionDraftController {

    private final SolutionDraftService solutionDraftService;

    @PostMapping("/problems/{problemId}/solution-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public SolutionDraftResponse create(
            @PathVariable UUID problemId,
            @Valid @RequestBody SaveSolutionDraftRequest request
    ) {
        return solutionDraftService.create(problemId, request);
    }

    /**
     * @param problemId narrows to one problem's drafts. Omitted, returns every
     *                  draft the caller owns, newest edit first — which is what
     *                  a "continue where you left off" list wants.
     */
    @GetMapping("/solution-drafts")
    public Page<SolutionDraftResponse> findMine(
            @RequestParam(required = false) UUID problemId,
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return solutionDraftService.findMine(problemId, pageable);
    }

    @GetMapping("/solution-drafts/{id}")
    public SolutionDraftResponse findById(@PathVariable UUID id) {
        return solutionDraftService.findById(id);
    }

    /**
     * The autosave target. A whole-draft replace rather than a patch, so
     * repeating it is harmless and a field the client stops sending is cleared
     * rather than silently kept.
     */
    @PutMapping("/solution-drafts/{id}")
    public SolutionDraftResponse save(
            @PathVariable UUID id,
            @Valid @RequestBody SaveSolutionDraftRequest request
    ) {
        return solutionDraftService.save(id, request);
    }

    @DeleteMapping("/solution-drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        solutionDraftService.delete(id);
    }

    /**
     * Posts the draft and discards it. Returns the solution so the client can
     * navigate straight to it. A rejected submission leaves the draft as it was.
     */
    @PostMapping("/solution-drafts/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public SolutionResponse submit(@PathVariable UUID id) {
        return solutionDraftService.submit(id);
    }
}
