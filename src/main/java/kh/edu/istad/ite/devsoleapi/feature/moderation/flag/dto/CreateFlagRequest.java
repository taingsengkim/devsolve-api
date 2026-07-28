package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagReason;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;

import java.util.UUID;

public record CreateFlagRequest(


        @NotNull(message = "Flaggable type is required")
        FlaggableType flaggableType,

        @NotNull(message = "Flaggable ID is required")
        UUID flaggableId,

        @NotNull(message = "Reason is required")
        FlagReason reason,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}
