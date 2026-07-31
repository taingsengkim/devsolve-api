package kh.edu.istad.ite.devsoleapi.feature.vote.dto;

import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;

import java.util.UUID;

public record VoteSummaryResponse(
        VoteType type,
        UUID targetId,
        long score,
        long upvotes,
        long downvotes,
        Short currentUserVote
) {
}
