package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.AutoApprovalSettingResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.UpdateAutoApprovalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin screen's switch for publishing without a moderator.
 *
 * <p>Under {@code /api/v1/admin}, which the security chain already restricts
 * to the ADMIN realm role; the service checks again rather than trusting the
 * URL it happens to be mounted on.
 */
@RestController
@RequestMapping("/api/v1/admin/auto-approval")
@RequiredArgsConstructor
public class AutoApprovalController {

    private final AutoApprovalService autoApprovalService;

    @GetMapping
    public List<AutoApprovalSettingResponse> findAll() {
        return autoApprovalService.findAll();
    }

    @PatchMapping("/{target}")
    public AutoApprovalSettingResponse setEnabled(
            @PathVariable AutoApprovalTarget target,
            @Valid @RequestBody UpdateAutoApprovalRequest request
    ) {
        return autoApprovalService.setEnabled(target, request.enabled());
    }
}
