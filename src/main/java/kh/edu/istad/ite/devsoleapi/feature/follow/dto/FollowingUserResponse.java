package kh.edu.istad.ite.devsoleapi.feature.follow.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowingUserResponse(
        UUID userId,
        String fullName,
        String avatarUrl,
        String biography,
        long followerCount,
        boolean following,
        LocalDateTime followedAt
) {
}
