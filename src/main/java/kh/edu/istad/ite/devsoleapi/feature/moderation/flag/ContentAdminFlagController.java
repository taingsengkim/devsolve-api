package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/flags")
public class ContentAdminFlagController {

    private final ContentFlagService contentFlagService;

    @GetMapping
    public Page<FlagResponse> getAdminFlags(
            @RequestParam(defaultValue = "PENDING")
            FlagStatus status,

            @RequestParam(required = false)
            FlaggableType flaggableType,

            @RequestParam(required = false)
            FlagReason reason,

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
                pageNumber,
                pageSize
        );
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
}
