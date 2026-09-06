package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagGroupResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagQueueSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.TargetFlagActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/flags")
public class ContentAdminFlagController {

    private final ContentFlagService contentFlagService;

    /**
     * The queue, one row per report, each carrying what was reported.
     *
     * @param search free text over the reported writing, whoever wrote it,
     *               whoever reported it, and the note they left
     */
    @GetMapping
    public Page<FlagResponse> getAdminFlags(
            @RequestParam(defaultValue = "PENDING")
            FlagStatus status,

            @RequestParam(required = false)
            FlaggableType flaggableType,

            @RequestParam(required = false)
            FlagReason reason,

            @RequestParam(required = false)
            @Size(max = 200, message = "search must not exceed 200 characters")
            String search,

            @RequestParam(defaultValue = "NEWEST")
            FlagQueueSort sort,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return contentFlagService.getAdminFlags(
                status,
                flaggableType,
                reason,
                search,
                sort,
                pageNumber,
                pageSize
        );
    }

    /**
     * The same queue, one card per reported thing.
     *
     * <p>Its own path rather than a flag on the listing above: the rows are a
     * different shape, and an endpoint whose response schema depends on a query
     * parameter is one no generated client can type.
     */
    @GetMapping("/grouped")
    public Page<FlagGroupResponse> getAdminFlagGroups(
            @RequestParam(defaultValue = "PENDING")
            FlagStatus status,

            @RequestParam(required = false)
            FlaggableType flaggableType,

            @RequestParam(required = false)
            FlagReason reason,

            @RequestParam(required = false)
            @Size(max = 200, message = "search must not exceed 200 characters")
            String search,

            @RequestParam(defaultValue = "MOST_REPORTED")
            FlagQueueSort sort,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return contentFlagService.getAdminFlagGroups(
                status,
                flaggableType,
                reason,
                search,
                sort,
                pageNumber,
                pageSize
        );
    }

    /** The badge counts above the queue. */
    @GetMapping("/summary")
    public FlagQueueSummaryResponse getQueueSummary() {
        return contentFlagService.getQueueSummary();
    }

    @GetMapping("/{id}")
    public FlagResponse getAdminFlagById(
            @PathVariable UUID id
    ) {
        return contentFlagService.getAdminFlagById(id);
    }

    @PatchMapping("/{id}/dismiss")
    public FlagResponse dismissFlag(
            @PathVariable UUID id
    ) {
        return contentFlagService.dismissFlag(id);
    }

    @PatchMapping("/{id}/resolve")
    public FlagResponse resolveFlag(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveFlagRequest request
    ) {
        return contentFlagService.resolveFlag(id, request);
    }

    /**
     * Upholds every open report on one piece of content, and takes the content
     * down if asked. The action behind a grouped card.
     */
    @PatchMapping("/targets/{flaggableType}/{flaggableId}/resolve")
    public TargetFlagActionResponse resolveTargetFlags(
            @PathVariable FlaggableType flaggableType,
            @PathVariable UUID flaggableId,
            @Valid @RequestBody ResolveFlagRequest request
    ) {
        return contentFlagService.resolveTargetFlags(
                flaggableType,
                flaggableId,
                request
        );
    }

    /** Dismisses every open report on one piece of content. */
    @PatchMapping("/targets/{flaggableType}/{flaggableId}/dismiss")
    public TargetFlagActionResponse dismissTargetFlags(
            @PathVariable FlaggableType flaggableType,
            @PathVariable UUID flaggableId
    ) {
        return contentFlagService.dismissTargetFlags(
                flaggableType,
                flaggableId
        );
    }
}
