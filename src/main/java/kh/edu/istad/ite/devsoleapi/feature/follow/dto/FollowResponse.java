package kh.edu.istad.ite.devsoleapi.feature.follow.dto;

import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowResponse(
        UUID id,
        FollowType followableType,
        UUID followableId,
        LocalDateTime createdAt
) {
}
