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
    private final ContentFlagService service;

    @GetMapping("/admin/flags")
    public Page<FlagResponse> getAdminFlags(

            @RequestParam(defaultValue = "0")
            int pageNumber,

            @RequestParam(defaultValue = "10")
            int pageSize
    ) {
        return service.getAdminFlags(
                pageNumber,
                pageSize
        );
    }

    @PatchMapping("/admin/flags/{id}/dismiss")
    public FlagResponse dismissFlag(
            @PathVariable UUID id
    ) {
        return service.dismissFlag(id);
    }
}
