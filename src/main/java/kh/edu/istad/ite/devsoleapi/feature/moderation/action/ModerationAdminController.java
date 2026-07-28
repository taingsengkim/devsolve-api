package kh.edu.istad.ite.devsoleapi.feature.moderation.action;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.ContentFlagService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
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
}
