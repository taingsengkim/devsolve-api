package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ModerationService {

    ModerationActionResponse createModerationAction(
            UUID targetUserId,
            CreateModerationActionRequest request
    );

    Page<ModerationActionResponse> getModerationHistory(
            ModerationTargetType targetType,
            UUID targetId,
            ModerationActionType action,
            int pageNumber,
            int pageSize
    );

    ModerationActionResponse getModerationActionById(
            UUID actionId
    );
}
