package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
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
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return contentFlagService.getAdminFlags(
                pageNumber,
                pageSize
        );
    }

    @PatchMapping("/{id}/dismiss")
    public FlagResponse dismissFlag(
            @PathVariable UUID id
    ) {
        return contentFlagService.dismissFlag(id);
    }
}
