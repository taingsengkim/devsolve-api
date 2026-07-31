package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoteMapper {
    VoteResponse toResponse(Vote vote);
}