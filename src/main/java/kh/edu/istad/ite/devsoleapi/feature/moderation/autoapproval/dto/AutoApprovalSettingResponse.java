package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.AutoApprovalTarget;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @param available whether a model is configured at all. The switch can be on
 *                  while this is false — the deployment simply has no key — and
 *                  a screen that showed only {@code enabled} would then promise
 *                  an automation that is not running.
 */
public record AutoApprovalSettingResponse(
        AutoApprovalTarget target,
        boolean enabled,
        boolean available,
        UUID updatedBy,
        LocalDateTime updatedAt
) {
}
