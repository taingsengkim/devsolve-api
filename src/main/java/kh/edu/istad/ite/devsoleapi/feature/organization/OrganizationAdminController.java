package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/organizations")
public class OrganizationAdminController {

    private final OrganizationService organizationService;

    @GetMapping("/pending")
    public Page<OrganizationReviewSummaryResponse> getPendingOrganizations(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return organizationService.getPendingOrganizations(
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{id}")
    public OrganizationReviewResponse getForReview(
            @PathVariable UUID id
    ) {
        return organizationService.getForReview(id);
    }

    @PatchMapping("/{id}/approve")
    public OrganizationResponse approve(@PathVariable UUID id) {
        return organizationService.approve(id);
    }

    @PatchMapping("/{id}/reject")
    public OrganizationResponse reject(@PathVariable UUID id) {
        return organizationService.reject(id);
    }
}
