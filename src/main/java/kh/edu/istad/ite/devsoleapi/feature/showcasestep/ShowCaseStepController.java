package kh.edu.istad.ite.devsoleapi.feature.showcasestep;


import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/showcase-steps/{showcaseId}")
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

    @PutMapping(
            value = "/{stepId}/image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ShowcaseStepResponse uploadImage(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId,
            @RequestPart("file") MultipartFile file
    ) {
        return showcaseStepService.uploadImage(
                showcaseId,
                stepId,
                ShowcaseStepImageKind.IMAGE,
                file
        );
    }

    @DeleteMapping("/{stepId}/image")
    public ShowcaseStepResponse removeImage(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId
    ) {
        return showcaseStepService.removeImage(
                showcaseId,
                stepId,
                ShowcaseStepImageKind.IMAGE
        );
    }

    @PutMapping(
            value = "/{stepId}/diagram",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ShowcaseStepResponse uploadDiagram(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId,
            @RequestPart("file") MultipartFile file
    ) {
        return showcaseStepService.uploadImage(
                showcaseId,
                stepId,
                ShowcaseStepImageKind.DIAGRAM,
                file
        );
    }

    @DeleteMapping("/{stepId}/diagram")
    public ShowcaseStepResponse removeDiagram(
            @PathVariable UUID showcaseId,
            @PathVariable UUID stepId
    ) {
        return showcaseStepService.removeImage(
                showcaseId,
                stepId,
                ShowcaseStepImageKind.DIAGRAM
        );
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
