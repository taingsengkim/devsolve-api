package co.istad.ite.devsoleapi.feature.vote;

import co.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import co.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


import java.util.UUID;


@Service
@RequiredArgsConstructor
public class VoteServiceImpl
        implements VoteService {


    private final VoteRepository voteRepository;

    private final VoteMapper voteMapper;


    @Override
    public VoteResponse vote(UUID userId, VoteRequest request) {
        Vote vote = voteRepository
                .findByUserIdAndVotableTypeAndVotableId(userId, request.votableType(), request.votableId()).orElse(null);
        if(vote == null){
            Vote newVote = Vote.builder().userId(userId).votableType(request.votableType())
                    .votableId(request.votableId())
                    .voteValue(request.voteValue())
                    .build();
            voteRepository.save(newVote);

            return voteMapper.toResponse(newVote);

        }

        if( vote.getVoteValue().equals(request.voteValue())){
            voteRepository.delete(vote);
            return null;

        }

        vote.setVoteValue(request.voteValue());
        voteRepository.save(vote);

        return voteMapper.toResponse(vote);

    }




    @Override
    public void deleteVote(UUID userId, VoteType type, UUID votableId){

        voteRepository.deleteByUserIdAndVotableTypeAndVotableId(userId, type, votableId);

    }

}