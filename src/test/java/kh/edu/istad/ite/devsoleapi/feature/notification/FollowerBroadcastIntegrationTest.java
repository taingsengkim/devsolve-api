package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The follower broadcast is the one notification path that does not go through
 * {@link NotificationDispatcher}. It bulk-inserts with a native statement using
 * {@code gen_random_uuid()} and {@code ON CONFLICT}, neither of which H2 can
 * stand in for, so this runs against real PostgreSQL.
 *
 * <p>Unlike the direct notifications, this one is called inline from the
 * service that publishes the content rather than from an after-commit
 * listener, so it joins the caller's transaction and commits with it. That is
 * why it kept working while everything routed through the listener silently
 * stored nothing — worth pinning so the two paths cannot drift apart unnoticed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class FollowerBroadcastIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private FollowNotificationService followNotificationService;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserProfile persistedUser(String name) {
        UserProfile user = new UserProfile();
        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setFullName(name);
        return userProfileRepository.saveAndFlush(user);
    }

    @Test
    void everyFollowerIsNotifiedExceptTheActor() {
        UserProfile follower = persistedUser("Follower");
        UserProfile actor = persistedUser("Actor");
        UUID programId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            followRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    follower.getId(),
                    FollowType.PROGRAM.databaseValue(),
                    programId
            );
            // The actor follows it too, and must still not be told about
            // their own publish.
            followRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    actor.getId(),
                    FollowType.PROGRAM.databaseValue(),
                    programId
            );
        });

        String eventKey = "broadcast:" + UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status ->
                followNotificationService.notifyFollowers(
                        FollowType.PROGRAM,
                        programId,
                        actor.getId(),
                        "Program published",
                        "A program you follow is now live.",
                        NotificationType.PROGRAM,
                        programId,
                        eventKey
                )
        );

        assertEquals(
                1,
                notificationRepository.countByUserIdAndReadFalse(
                        follower.getId()
                ),
                "follower broadcast was not stored"
        );
        assertEquals(
                0,
                notificationRepository.countByUserIdAndReadFalse(actor.getId())
        );
    }

    @Test
    void broadcastingTheSameEventTwiceStoresItOnce() {
        UserProfile follower = persistedUser("Follower");
        UUID programId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status ->
                followRepository.insertIfAbsent(
                        UUID.randomUUID(),
                        follower.getId(),
                        FollowType.PROGRAM.databaseValue(),
                        programId
                )
        );

        String eventKey = "broadcast:" + UUID.randomUUID();
        for (int attempt = 0; attempt < 2; attempt++) {
            transactionTemplate.executeWithoutResult(status ->
                    followNotificationService.notifyFollowers(
                            FollowType.PROGRAM,
                            programId,
                            UUID.randomUUID(),
                            "Program published",
                            "A program you follow is now live.",
                            NotificationType.PROGRAM,
                            programId,
                            eventKey
                    )
            );
        }

        assertEquals(
                1,
                notificationRepository.countByUserIdAndReadFalse(
                        follower.getId()
                )
        );
    }
}
