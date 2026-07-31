package kh.edu.istad.ite.devsoleapi.feature.vote.dto;

import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;

import java.time.LocalDateTime;
import java.util.UUID;
public record VoteResponse(
        UUID id,
        VoteType type,
        UUID targetId,
        short value,
        LocalDateTime createdAt
) {
}
