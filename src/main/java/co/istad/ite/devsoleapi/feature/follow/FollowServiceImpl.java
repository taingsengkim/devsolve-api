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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {
    private final FollowRepository followRepository;
    private final FollowMapper followMapper;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public FollowResponse follow(FollowRequest request) {
        log.info("Processing follow request: {}", request);

        // ✅ Validate follower exists
        UserProfile follower = userProfileRepository.findById(request.follower())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.follower()));

        // ✅ Check if already following
        if (followRepository.existsByFollowerIdAndFollowableTypeAndFollowableId(
                follower.getId(),
                request.followableType(),
                request.followableId())) {
            throw new RuntimeException("Already following this " + request.followableType());
        }

        // ✅ Validate followable exists
        validateFollowableExists(request.followableType(), request.followableId());

        // ✅ Create and save follow
        Follow follow = followMapper.toEntity(request);
        follow.setFollower(follower);

        Follow saved = followRepository.save(follow);
        log.info("Successfully created follow with id: {}", saved.getId());

        // ✅ Convert to response and enrich with data
        FollowResponse response = followMapper.toResponse(saved);
        return enrichFollowResponse(response);
    }

    @Override
    @Transactional
    public void unfollow(String followerId, String followableType, String followableId) {
        log.info("Processing unfollow request - follower: {}, type: {}, id: {}",
                followerId, followableType, followableId);

        Follow follow = followRepository
                .findByFollowerIdAndFollowableTypeAndFollowableId(followerId, followableType, followableId)
                .orElseThrow(() -> new RuntimeException("Follow relationship not found"));

        followRepository.delete(follow);
        log.info("Successfully unfollowed");
    }

    @Override
    public List<FollowResponse> getFollowing(String followerId) {
        log.info("Getting following list for user: {}", followerId);

        // ✅ Validate user exists
        if (!userProfileRepository.existsById(followerId)) {
            throw new RuntimeException("User not found with id: " + followerId);
        }

        List<Follow> follows = followRepository.findByFollowerId(followerId);
        return follows.stream()
                .map(followMapper::toResponse)
                .map(this::enrichFollowResponse)  // ✅ FIXED: Use enrich method
                .collect(Collectors.toList());
    }

    @Override
    public List<FollowResponse> getFollowers(String followableId, String followableType) {
        log.info("Getting followers for - type: {}, id: {}", followableType, followableId);

        List<Follow> follows = followRepository.findByFollowableTypeAndFollowableId(followableType, followableId);
        return follows.stream()
                .map(followMapper::toResponse)
                .map(this::enrichFollowResponse)  // ✅ FIXED: Use enrich method
                .collect(Collectors.toList());
    }

    @Override
    public boolean isFollowing(String followerId, String followableType, String followableId) {
        return followRepository.existsByFollowerIdAndFollowableTypeAndFollowableId(
                followerId, followableType, followableId);
    }

    @Override
    public long countFollowers(String followableType, String followableId) {
        return followRepository.countByFollowableTypeAndFollowableId(followableType, followableId);
    }

    @Override
    public long countFollowing(String followerId) {
        return followRepository.countByFollowerId(followerId);
    }

    // ✅ Helper method to validate followable exists
    private void validateFollowableExists(String followableType, String followableId) {
        if (followableType.equalsIgnoreCase("USER")) {
            userProfileRepository.findById(followableId)
                    .orElseThrow(() -> new RuntimeException("User to follow not found with id: " + followableId));
        }
        // Add more validations for other types (POST, TOPIC, etc.)
    }

    // ✅ FIXED: Complete implementation to enrich response with user data
    private FollowResponse enrichFollowResponse(FollowResponse response) {
        String followerUsername = null;
        String followableName = null;

        // Get follower username
        UserProfile follower = userProfileRepository.findById(response.followerId()).orElse(null);
        if (follower != null) {
            followerUsername = follower.getFullName();
        }

        // Get followable name (if following a USER)
        if (response.followableType().equalsIgnoreCase("USER")) {
            UserProfile followable = userProfileRepository.findById(response.followableId()).orElse(null);
            if (followable != null) {
                followableName = followable.getFullName();
            }
        }

        // ✅ Return new response with enriched data
        return new FollowResponse(
                response.id(),
                response.followerId(),
                followerUsername,
                response.followableType(),
                response.followableId(),
                followableName,
                response.createdAt()
        );
    }
}