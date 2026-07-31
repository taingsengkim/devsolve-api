package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteSummaryResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface VoteService {

    VoteResponse setVote(
            VoteType type,
            UUID targetId,
            VoteRequest request
    );

    void removeVote(VoteType type, UUID targetId);

    VoteSummaryResponse getSummary(VoteType type, UUID targetId);

    Page<VoteResponse> getMine(
            VoteType type,
            int pageNumber,
            int pageSize
    );
}
