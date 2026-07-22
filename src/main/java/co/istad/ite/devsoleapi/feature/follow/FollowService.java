package co.istad.ite.devsoleapi.feature.follow;

import co.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import co.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;

import java.util.List;
import java.util.UUID;

public interface FollowService {
    FollowResponse follow(FollowRequest request);
    void unfollow(UUID followerId, String followableType, UUID followableId);
    List<FollowResponse> getFollowing(UUID followerId);
    List<FollowResponse> getFollowers(UUID followableId, String followableType);
    boolean isFollowing(UUID followerId, String followableType, UUID followableId);
    long countFollowers(String followableType, UUID followableId);
    long countFollowing(UUID followerId);
}
