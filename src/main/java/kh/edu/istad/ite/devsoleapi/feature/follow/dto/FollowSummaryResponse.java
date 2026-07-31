package kh.edu.istad.ite.devsoleapi.feature.follow.dto;

import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;

import java.util.UUID;

public record FollowSummaryResponse(
        FollowType followableType,
        UUID followableId,
        long followerCount,
        boolean following
) {
}
