package co.istad.ite.devsoleapi.feature.follow;

import co.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import co.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;

import java.util.List;
import java.util.UUID;

public interface FollowService {
    /**
     *
     * @param request
     * @return
     */
    FollowResponse follow(FollowRequest request);
    void unfollow(String followerId, String followableType, String followableId);
    List<FollowResponse> getFollowing(String followerId);
    List<FollowResponse> getFollowers(String followableId, String followableType);
    boolean isFollowing(String followerId, String followableType, String followableId);
    long countFollowers(String followableType, String followableId);
    long countFollowing(String followerId);
}
