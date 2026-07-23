package co.istad.ite.devsoleapi.feature.vote;

import co.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import co.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;

import java.util.UUID;

public interface VoteService {


    VoteResponse vote(UUID userId, VoteRequest request);

    void deleteVote(UUID userId, VoteType type, UUID votableId);

}