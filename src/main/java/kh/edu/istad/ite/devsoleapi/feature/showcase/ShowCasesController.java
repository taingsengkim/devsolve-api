package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/showcases")
public class ShowCasesController {
    private final ShowCasesService service;


    @GetMapping
    public Page<ShowCasesResponse> getAll(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return service.getAllPublished(pageNumber, pageSize);
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

}
