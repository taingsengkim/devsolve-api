package kh.edu.istad.ite.devsoleapi.feature.showcase;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/showcases")
public class ShowCasesAdminController {
    private final ShowCasesService service;

    @PatchMapping("/{id}/review-status")
    public ShowCasesResponse updateReviewStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShowcaseStatusRequest request
    ) {
        return service.updateStatus(id, request);
    }
}
