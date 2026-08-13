package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowerResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowingUserResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserProfileRepository userProfileRepository;
    private final FollowTargetAccessService targetAccessService;

    @Override
    @Transactional
    public FollowResponse follow(FollowType type, UUID targetId) {
        UUID followerId = requireCurrentUserId();
        targetAccessService.requireFollowable(type, targetId);
        if (type == FollowType.USER && followerId.equals(targetId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You cannot follow yourself"
            );
        }

        userProfileRepository.findById(followerId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "An active user profile is required"
                ));
        followRepository.insertIfAbsent(
                UUID.randomUUID(),
                followerId,
                type.databaseValue(),
                targetId
        );
        Follow follow = followRepository
                .findByFollower_IdAndFollowableTypeAndFollowableId(
                        followerId,
                        type,
                        targetId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Follow upsert completed without a stored follow"
                ));

        if (type == FollowType.USER) {
            // Keyed on the pair rather than on this call, so following,
            // unfollowing and following again does not notify twice. Only
            // people are told they gained a follower — a program or a problem
            // has no one to tell.
            eventPublisher.publishEvent(NotificationEvent.to(
                    targetId,
                    "New follower",
                    follower.getFullName() + " started following you.",
                    NotificationType.USER,
                    followerId,
                    "follow:" + followerId + ":" + targetId
            ));
        }

        return toResponse(follow);
    }

    @Override
    @Transactional
    public void unfollow(FollowType type, UUID targetId) {
        followRepository.deleteByFollower_IdAndFollowableTypeAndFollowableId(
                requireCurrentUserId(),
                type,
                targetId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FollowSummaryResponse getSummary(
            FollowType type,
            UUID targetId
    ) {
        targetAccessService.requireFollowable(type, targetId);
        boolean following = optionalCurrentUserId()
                .map(userId -> followRepository
                        .existsByFollower_IdAndFollowableTypeAndFollowableId(
                                userId,
                                type,
                                targetId
                        ))
                .orElse(false);
        return new FollowSummaryResponse(
                type,
                targetId,
                followRepository.countByFollowableTypeAndFollowableId(
                        type,
                        targetId
                ),
                following
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowResponse> getMine(
            FollowType type,
            int pageNumber,
            int pageSize
    ) {
        return findFollowing(
                requireCurrentUserId(),
                type,
                pageNumber,
                pageSize
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowing(
            UUID userId,
            FollowType type,
            int pageNumber,
            int pageSize
    ) {
        targetAccessService.requireFollowable(FollowType.USER, userId);
        return findFollowing(userId, type, pageNumber, pageSize);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<FollowingUserResponse> getFollowingUsers(
            UUID userId,
            int pageNumber,
            int pageSize
    ) {

        targetAccessService.requireFollowable(
                FollowType.USER,
                userId
        );

        UUID currentUserId = optionalCurrentUserId()
                .orElse(null);

        return followRepository.findFollowing(
                userId,
                FollowType.USER,
                pageRequest(pageNumber, pageSize)
        ).map(follow -> {

            UUID followedUserId = follow.getFollowableId();

            UserProfile followedUser = userProfileRepository
                    .findById(followedUserId)
                    .filter(user ->
                            user.getStatus() == UserStatus.ACTIVE
                    )
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Followed user not found"
                            )
                    );

            long followerCount =
                    followRepository
                            .countByFollowableTypeAndFollowableId(
                                    FollowType.USER,
                                    followedUserId
                            );

            boolean following =
                    currentUserId != null
                            && followRepository
                            .existsByFollower_IdAndFollowableTypeAndFollowableId(
                                    currentUserId,
                                    FollowType.USER,
                                    followedUserId
                            );

            return new FollowingUserResponse(
                    followedUser.getId(),
                    followedUser.getFullName(),
                    followedUser.getAvatarUrl(),
                    followedUser.getBiography(),
                    followerCount,
                    following,
                    follow.getCreatedAt()
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FollowerResponse> getFollowers(
            FollowType type,
            UUID targetId,
            int pageNumber,
            int pageSize
    ) {
        targetAccessService.requireFollowable(type, targetId);
        return followRepository.findByFollowableTypeAndFollowableId(
                type,
                targetId,
                pageRequest(pageNumber, pageSize)
        ).map(follow -> {
            UserProfile follower = follow.getFollower();
            return new FollowerResponse(
                    follower.getId(),
                    follower.getFullName(),
                    follower.getAvatarUrl(),
                    follow.getCreatedAt()
            );
        });
    }

    private Page<FollowResponse> findFollowing(
            UUID userId,
            FollowType type,
            int pageNumber,
            int pageSize
    ) {
        return followRepository.findFollowing(
                userId,
                type,
                pageRequest(pageNumber, pageSize)
        ).map(this::toResponse);
    }

    private Pageable pageRequest(int pageNumber, int pageSize) {
        return PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
    }

    private FollowResponse toResponse(Follow follow) {
        return new FollowResponse(
                follow.getId(),
                follow.getFollowableType(),
                follow.getFollowableId(),
                follow.getCreatedAt()
        );
    }

    private UUID requireCurrentUserId() {
        return optionalCurrentUserId().orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid Keycloak access token is required"
                )
        );
    }

    private Optional<UUID> optionalCurrentUserId() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication
                instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    jwtAuthentication.getToken().getSubject()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
