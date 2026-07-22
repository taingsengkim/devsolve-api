package co.istad.ite.devsoleapi.feature.follow;

import co.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import co.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import co.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import co.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowerServiceImpl implements FollowService {
    private final FollowRepository followRepository;
    private final FollowMapper followMapper;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public FollowResponse follow(FollowRequest request) {
        log.info("Processing follow request: {}", request);

        // Validate follower exists
        UserProfile follower = userProfileRepository.findById(String.valueOf(request.follower()))
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.follower()));

        // Check if already following
        if (followRepository.existsByFollowerIdAndFollowableTypeAndFollowableId(
                UUID.fromString(follower.getId()),
                request.followableType(),
                request.followableId())) {
            throw new RuntimeException("Already following this " + request.followableType());
        }

        // Validate followable exists (you can add more validation based on type)
        validateFollowableExists(request.followableType(), request.followableId());

        // Create and save follow
        Follow follow = followMapper.toEntity(request);
        follow.setFollower(follower);

        Follow saved = followRepository.save(follow);
        log.info("Successfully created follow with id: {}", saved.getId());

        FollowResponse response = followMapper.toResponse(saved);
        // You can set followableName based on type
        response = setFollowableName(response);

        return response;
    }

    @Override
    @Transactional
    public void unfollow(UUID followerId, String followableType, UUID followableId) {
        log.info("Processing unfollow request - follower: {}, type: {}, id: {}",
                followerId, followableType, followableId);

        Follow follow = followRepository
                .findByFollowerIdAndFollowableTypeAndFollowableId(followerId, followableType, followableId)
                .orElseThrow(() -> new RuntimeException("Follow relationship not found"));

        followRepository.delete(follow);
        log.info("Successfully unfollowed");
    }

    @Override
    public List<FollowResponse> getFollowing(UUID followerId) {
        log.info("Getting following list for user: {}", followerId);

        // Validate user exists
        if (!userProfileRepository.existsById(String.valueOf(followerId))) {
            throw new RuntimeException("User not found with id: " + followerId);
        }

        List<Follow> follows = followRepository.findByFollowerId(followerId);
        return follows.stream()
                .map(followMapper::toResponse)
                .map(this::setFollowableName)
                .collect(Collectors.toList());
    }

    @Override
    public List<FollowResponse> getFollowers(UUID followableId, String followableType) {
        log.info("Getting followers for - type: {}, id: {}", followableType, followableId);

        List<Follow> follows = followRepository.findByFollowableTypeAndFollowableId(followableType, followableId);
        return follows.stream()
                .map(followMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFollowing(UUID followerId, String followableType, UUID followableId) {
        return followRepository.existsByFollowerIdAndFollowableTypeAndFollowableId(
                followerId, followableType, followableId);
    }

    @Override
    public long countFollowers(String followableType, UUID followableId) {
        return followRepository.countByFollowableTypeAndFollowableId(followableType, followableId);
    }

    @Override
    public long countFollowing(UUID followerId) {
        return followRepository.countByFollowerId(followerId);
    }

    // Helper methods
    private void validateFollowableExists(String followableType, UUID followableId) {
        // Add validation logic based on followableType
        // For example: if type is "POST", check if post exists
        // if type is "USER", check if user exists, etc.
        // This prevents following non-existent entities

        if (followableType.equalsIgnoreCase("USER")) {
            userProfileRepository.findById(String.valueOf(followableId))
                    .orElseThrow(() -> new RuntimeException("User to follow not found"));
        }
        // Add more validations for other types
    }

    private FollowResponse setFollowableName(FollowResponse response) {
        // If followableType is "USER", fetch the username
        if (response.followableType().equalsIgnoreCase("USER")) {
            // Fetch and set the username
            // userProfileRepository.findById(response.followableId())
            //     .ifPresent(user -> response = new FollowResponse(
            //         response.id(), response.followerId(),
            //         response.followerUsername(), response.followableType(),
            //         response.followableId(), user.getUsername(),
            //         response.createdAt()
            //     ));
        }
        return response;
    }}