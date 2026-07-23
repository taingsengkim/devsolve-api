package co.istad.ite.devsoleapi.feature.vote.dto;

import co.istad.ite.devsoleapi.feature.vote.VoteType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VoteRequest(

        @NotNull
        VoteType votableType,

        @NotNull
        UUID votableId,

        @NotNull
        @Min(-1)
        @Max(1)
        Short voteValue

) {
}