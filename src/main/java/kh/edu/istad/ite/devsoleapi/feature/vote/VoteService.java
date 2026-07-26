package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;

import java.util.UUID;

public interface VoteService {

    /**
     *Create or toggle a vote on problem, solution or comment
     * @param userId Id of authentication user
     * @param request vote information
     * @return vote response
     */
    VoteResponse vote(UUID userId, VoteRequest request);

    /**
     * Remove user's vote from content.
     * @param userId ID of authenticated user
     * @param type vote of voted content
     * @param votableId ID of conten
     */

    void deleteVote(UUID userId, VoteType type, UUID votableId);

}