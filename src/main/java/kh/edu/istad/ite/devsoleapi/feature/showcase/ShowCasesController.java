package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewCountResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/showcases")
public class ShowCasesController {
    private final ShowCasesService service;


    @GetMapping
    public Page<ShowCasesResponse> getAll(
            @RequestParam(required = false)
            String query,

            @RequestParam(required = false)
            UUID categoryId,

            @RequestParam(defaultValue = "createdAt")
            String sortBy,

            @RequestParam(defaultValue = "DESC")
            String sortDirection,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return service.getAllPublished(
                query,
                categoryId,
                sortBy,
                sortDirection,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/mine")
    public Page<ShowCasesSummaryResponse> getMine(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return service.getMyShowcases(pageNumber, pageSize);
    }

    @GetMapping("/{id}")
    public ShowCasesResponse getById(
            @PathVariable UUID id
    ) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowCasesResponse create(
            @Valid @RequestBody CreateShowCasesRequest request
    ) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public ShowCasesResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShowCasesRequest request
    ) {
        return service.update(id, request);
    }

    @PutMapping(
            value = "/{id}/cover-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ShowCasesResponse uploadCoverImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return service.uploadCoverImage(id, file);
    }

    @DeleteMapping("/{id}/cover-image")
    public ShowCasesResponse removeCoverImage(
            @PathVariable UUID id
    ) {
        return service.removeCoverImage(id);
    }

    @PatchMapping("/{id}/soft-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @PathVariable UUID id
    ) {
        service.softDelete(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hardDelete(
            @PathVariable UUID id
    ) {
        service.hardDelete(id);
    }

    @DeleteMapping("/{id}/revision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRevision(
            @PathVariable UUID id
    ) {
        service.cancelRevision(id);
    }

    @GetMapping("/{id}/revision")
    public ShowcaseReviewDetailResponse getMyRevision(
            @PathVariable UUID id
    ) {
        return service.getMyRevision(id);
    }

    @PatchMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(
            @PathVariable UUID id
    ) {
        service.restore(id);
    }

    @PostMapping("/{id}/views")
    public ShowcaseViewCountResponse incrementViewCount(
            @PathVariable UUID id
    ) {
        return service.incrementViewCount(id);
    }

}
