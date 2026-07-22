package co.istad.ite.devsoleapi.feature.follow.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowResponse(
        UUID id,
        UUID followerId,
        String followerUsername,  // Added for better response
        String followableType,
        UUID followableId,
        String followableName,    // Added for better response
        LocalDateTime createdAt
) {
}