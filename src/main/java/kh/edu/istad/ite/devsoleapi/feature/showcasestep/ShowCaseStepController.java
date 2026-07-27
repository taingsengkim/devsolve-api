package kh.edu.istad.ite.devsoleapi.feature.showcasestep;


import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/showcase-steps")
public class ShowCaseStepController {
    private final ShowCaseStepService showcaseStepService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowcaseStepResponse create(
            @PathVariable UUID showcaseId,
            @Valid @RequestBody CreateShowcaseStepRequest request
    ) {
        return showcaseStepService.create(showcaseId, request);
    }

    @GetMapping
    public List<ShowcaseStepResponse> getAll(
            @PathVariable UUID showcaseId
    ) {
        return showcaseStepService.getAll(showcaseId);
    }

    @GetMapping("/{stepId}")
    public ShowcaseStepResponse getById(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId
    ) {
        return showcaseStepService.getById(showcaseId, stepId);
    }

    @PatchMapping("/{stepId}")
    public ShowcaseStepResponse update(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId,
            @Valid @RequestBody UpdateShowcaseStepRequest request
    ) {
        return showcaseStepService.update(showcaseId, stepId, request);
    }

    @DeleteMapping("/{stepId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId
    ) {
        showcaseStepService.delete(showcaseId, stepId);
    }
}
