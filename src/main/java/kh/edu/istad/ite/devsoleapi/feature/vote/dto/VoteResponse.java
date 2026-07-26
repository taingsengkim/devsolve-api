package kh.edu.istad.ite.devsoleapi.feature.vote.dto;

import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;

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