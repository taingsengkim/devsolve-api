package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.DisputeResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ResolveDisputeRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The mediation queue. Sits under {@code /api/v1/admin}, which the security
 * configuration already restricts to the ADMIN realm role.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/disputes")
public class DisputeAdminController {

    private final DisputeService disputeService;

    /**
     * @param pendingOnly defaults to true so the bare URL is the work queue —
     *                    every dispute still waiting on a decision. Pass false
     *                    to browse the history, optionally narrowed by status.
     */
    @GetMapping
    public Page<DisputeResponse> findForAdmin(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) UUID reportId,
            @RequestParam(defaultValue = "true") boolean pendingOnly,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return disputeService.findForAdmin(
                status,
                programId,
                reportId,
                pendingOnly,
                pageable
        );
    }

    @GetMapping("/{id}")
    public DisputeResponse findById(@PathVariable UUID id) {
        return disputeService.findById(id);
    }

    @PatchMapping("/{id}")
    public DisputeResponse resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        return disputeService.resolve(id, request);
    }
}
