package kh.edu.istad.ite.devsoleapi.feature.vote.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record VoteRequest(
        @NotNull(message = "Vote value is required")
        Short value
) {

    @AssertTrue(message = "Vote value must be either -1 or 1")
    public boolean isValueSupported() {
        return value == null || value == -1 || value == 1;
    }
}
