package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowerResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface FollowService {

    FollowResponse follow(FollowType type, UUID targetId);

    void unfollow(FollowType type, UUID targetId);

    FollowSummaryResponse getSummary(FollowType type, UUID targetId);

    Page<FollowResponse> getMine(
            FollowType type,
            int pageNumber,
            int pageSize
    );

    Page<FollowResponse> getFollowing(
            UUID userId,
            FollowType type,
            int pageNumber,
            int pageSize
    );

    Page<FollowerResponse> getFollowers(
            FollowType type,
            UUID targetId,
            int pageNumber,
            int pageSize
    );
}
