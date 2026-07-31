package kh.edu.istad.ite.devsoleapi.feature.follow.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowerResponse(
        UUID userId,
        String fullName,
        String avatarUrl,
        LocalDateTime followedAt
) {
}
