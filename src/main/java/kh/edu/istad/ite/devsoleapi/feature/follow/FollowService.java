package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;

import java.util.List;

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
