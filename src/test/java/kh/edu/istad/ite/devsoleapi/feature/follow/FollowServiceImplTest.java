package kh.edu.istad.ite.devsoleapi.feature.follow;

import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import kh.edu.istad.ite.devsoleapi.feature.follow.dto.FollowSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private FollowTargetAccessService targetAccessService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void followUsesAuthenticatedUserAndIsIdempotentAtDatabaseBoundary() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserProfile profile = user(userId);
        Follow stored = Follow.builder()
                .id(UUID.randomUUID())
                .follower(profile)
                .followableType(FollowType.PROBLEM)
                .followableId(targetId)
                .createdAt(LocalDateTime.now())
                .build();
        authenticate(userId);
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
        when(followRepository
                .findByFollower_IdAndFollowableTypeAndFollowableId(
                        userId,
                        FollowType.PROBLEM,
                        targetId
                ))
                .thenReturn(Optional.of(stored));

        FollowResponse response = service().follow(
                FollowType.PROBLEM,
                targetId
        );

        assertEquals(stored.getId(), response.id());
        verify(targetAccessService).requireFollowable(
                FollowType.PROBLEM,
                targetId
        );
        verify(followRepository).insertIfAbsent(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq("problem"),
                org.mockito.ArgumentMatchers.eq(targetId)
        );
    }

    @Test
    void userCannotFollowSelf() {
        UUID userId = UUID.randomUUID();
        authenticate(userId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().follow(FollowType.USER, userId)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(followRepository, never()).insertIfAbsent(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void followingAUserPublishesANewFollowerNotification() {
        UUID followerId = UUID.randomUUID();
        UUID followedUserId = UUID.randomUUID();
        UserProfile follower = user(followerId);
        Follow stored = Follow.builder()
                .id(UUID.randomUUID())
                .follower(follower)
                .followableType(FollowType.USER)
                .followableId(followedUserId)
                .createdAt(LocalDateTime.now())
                .build();
        authenticate(followerId);
        when(userProfileRepository.findById(followerId))
                .thenReturn(Optional.of(follower));
        when(followRepository.insertIfAbsent(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq(followerId),
                org.mockito.ArgumentMatchers.eq("user"),
                org.mockito.ArgumentMatchers.eq(followedUserId)
        )).thenReturn(1);
        when(followRepository.findByFollower_IdAndFollowableTypeAndFollowableId(
                followerId, FollowType.USER, followedUserId
        )).thenReturn(Optional.of(stored));

        service().follow(FollowType.USER, followedUserId);

        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void repeatedUserFollowDoesNotPublishAnotherNotification() {
        UUID followerId = UUID.randomUUID();
        UUID followedUserId = UUID.randomUUID();
        UserProfile follower = user(followerId);
        Follow stored = Follow.builder()
                .id(UUID.randomUUID())
                .follower(follower)
                .followableType(FollowType.USER)
                .followableId(followedUserId)
                .createdAt(LocalDateTime.now())
                .build();
        authenticate(followerId);
        when(userProfileRepository.findById(followerId))
                .thenReturn(Optional.of(follower));
        when(followRepository.findByFollower_IdAndFollowableTypeAndFollowableId(
                followerId, FollowType.USER, followedUserId
        )).thenReturn(Optional.of(stored));

        service().follow(FollowType.USER, followedUserId);

        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void publicSummaryDoesNotRequireAuthentication() {
        UUID targetId = UUID.randomUUID();
        when(followRepository.countByFollowableTypeAndFollowableId(
                FollowType.SHOWCASE,
                targetId
        )).thenReturn(12L);

        FollowSummaryResponse response = service().getSummary(
                FollowType.SHOWCASE,
                targetId
        );

        assertEquals(12L, response.followerCount());
        assertFalse(response.following());
    }

    @Test
    void unfollowIsIdempotentAndCannotTargetAnotherUserAccount() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        authenticate(userId);

        service().unfollow(FollowType.ORGANIZATION, targetId);

        verify(followRepository)
                .deleteByFollower_IdAndFollowableTypeAndFollowableId(
                        userId,
                        FollowType.ORGANIZATION,
                        targetId
                );
    }

    private FollowServiceImpl service() {
        return new FollowServiceImpl(
                followRepository,
                userProfileRepository,
                targetAccessService,
                eventPublisher
        );
    }

    private UserProfile user(UUID id) {
        UserProfile user = new UserProfile();
        user.setId(id);
        user.setEmail(id + "@example.com");
        user.setFullName("Test User");
        return user;
    }

    private void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, java.util.List.of())
        );
    }
}
