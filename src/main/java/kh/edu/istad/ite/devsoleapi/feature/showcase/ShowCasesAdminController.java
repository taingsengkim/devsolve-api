package kh.edu.istad.ite.devsoleapi.feature.showcase;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewQueueItemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/showcases")
public class ShowCasesAdminController {
    private final ShowCasesService service;

    @GetMapping
    public Page<ShowcaseReviewQueueItemResponse> getReviewQueue(
            @RequestParam(defaultValue = "PENDING")
            ReviewStatus reviewStatus,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return service.getReviewQueue(
                reviewStatus,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{id}")
    public ShowcaseReviewDetailResponse getReviewDetail(
            @PathVariable UUID id
    ) {
        return service.getReviewDetail(id);
    }

    @GetMapping("/{id}/review-history")
    public Page<ShowcaseReviewHistoryResponse> getReviewHistory(
            @PathVariable UUID id,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return service.getReviewHistory(
                id,
                pageNumber,
                pageSize
        );
    }

    @PatchMapping("/{id}/review-status")
    public ShowCasesResponse updateReviewStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShowcaseStatusRequest request
    ) {
        return service.updateStatus(id, request);
    }
}
