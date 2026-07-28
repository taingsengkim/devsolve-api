package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor

public class ModerationServiceImpl implements ModerationService{
    private final ModerationActionRepository moderationActionRepository;
    private final UserProfileRepository userProfileRepository;
    private final ModerationActionMapper moderationActionMapper;

    @Override
    public ModerationActionResponse createModerationAction(UUID targetUserId, ModerationActionResponse moderationActionResponse) {
//        // 1. Allow ADMIN only
//        if (!AuthUtils.hasRole("ADMIN")) {
//            throw new ResponseStatusException(
//                    HttpStatus.FORBIDDEN,
//                    "Only ADMIN can perform moderation actions"
//            );
//        }

//        // 2. Extract logged-in admin ID
//        UUID adminId = extractCurrentUserId();
//
//        // 3. Prevent admin from moderating their own account
//        if (adminId.equals(targetUserId)) {
//            throw new ResponseStatusException(
//                    HttpStatus.BAD_REQUEST,
//                    "You cannot perform a moderation action on your own account"
//            );
//        }
//
//        // 4. Find admin profile
//        UserProfile admin = userProfileRepository.findById(adminId)
//                .orElseThrow(() -> new ResponseStatusException(
//                        HttpStatus.NOT_FOUND,
//                        "Admin profile not found"
//                ));
//
//        // 5. Find target user profile
//        UserProfile targetUser = userProfileRepository.findById(targetUserId)
//                .orElseThrow(() -> new ResponseStatusException(
//                        HttpStatus.NOT_FOUND,
//                        "Target user profile not found"
//                ));
//
//        // 6. Validate action request
//        validateRequest(request);
//
//        // 7. Apply action to target user
//        applyUserAction(targetUser, request);
//
//        // 8. Save the changed user status
//        userProfileRepository.save(targetUser);
//
//        // 9. Create moderation action audit record
//        ModerationAction moderationAction =
//                moderationActionMapper
//                        .mapCreateModerationActionRequestToModerationAction(
//                                request
//                        );
//
//        moderationAction.setAdmin(admin);
//        moderationAction.setTargetType(ModerationTargetType.USER);
//        moderationAction.setTargetId(targetUserId);
//
//        ModerationAction savedAction =
//                moderationActionRepository.save(moderationAction);
//
//        // 10. Map entity to response
//        return moderationActionMapper
//                .mapModerationActionToModerationActionResponse(
//                        savedAction
//                );
        return null;
    }
}
