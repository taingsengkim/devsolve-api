package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/solutions")
public class SolutionAdminController {

    private final SolutionService solutionService;

    @GetMapping
    public Page<SolutionResponse> getReviewQueue(
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
        return solutionService.getForModeration(
                reviewStatus,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolutionResponse> getReviewDetail(
            @PathVariable UUID id
    ) {
        SolutionResponse response = solutionService.getAdminById(id);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PatchMapping("/{id}/review-status")
    public ResponseEntity<SolutionResponse> updateReviewStatus(
            @PathVariable UUID id,
            @Valid
            @RequestBody UpdateSolutionReviewStatusRequest request
    ) {
        SolutionResponse response = solutionService.updateReviewStatus(
                id,
                request
        );
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
