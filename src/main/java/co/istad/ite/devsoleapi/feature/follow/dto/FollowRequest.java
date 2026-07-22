package co.istad.ite.devsoleapi.feature.follow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FollowRequest(
        @NotNull(message = "Follower ID is required")
        UUID follower,

        @NotBlank(message = "Followable type is required")
        String followableType,

        @NotNull(message = "Followable ID is required")
        UUID followableId
) {
}
