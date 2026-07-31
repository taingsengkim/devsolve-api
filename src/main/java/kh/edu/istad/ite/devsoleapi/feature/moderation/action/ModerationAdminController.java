package kh.edu.istad.ite.devsoleapi.feature.moderation.action;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class ModerationAdminController {

    private final ModerationService moderationService;

    @PostMapping("/{id}/moderation-actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ModerationActionResponse createModerationAction(
            @PathVariable UUID id,
            @Valid @RequestBody CreateModerationActionRequest request
    ) {
        return moderationService.createModerationAction(
                id,
                request
        );
    }

    @GetMapping("/moderation-actions")
    public Page<ModerationActionResponse> getModerationHistory(
            @RequestParam(required = false)
            ModerationTargetType targetType,

            @RequestParam(required = false)
            UUID targetId,

            @RequestParam(required = false)
            ModerationActionType action,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return moderationService.getModerationHistory(
                targetType,
                targetId,
                action,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/moderation-actions/{actionId}")
    public ModerationActionResponse getModerationActionById(
            @PathVariable UUID actionId
    ) {
        return moderationService
                .getModerationActionById(actionId);
    }
}
