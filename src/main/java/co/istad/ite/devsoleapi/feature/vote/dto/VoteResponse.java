package co.istad.ite.devsoleapi.feature.vote.dto;

import co.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;
public record VoteResponse(
        UUID id,
        UUID userId,
        VoteType votableType,
        UUID votableId,
        Short voteValue,
        LocalDateTime createdAt
) {}