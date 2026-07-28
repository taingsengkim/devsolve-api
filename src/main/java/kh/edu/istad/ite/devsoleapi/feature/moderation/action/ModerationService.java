package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;

import java.util.UUID;

public interface ModerationService {

    ModerationActionResponse createModerationAction(
            UUID targetUserId,
            CreateModerationActionRequest request
    );
}
