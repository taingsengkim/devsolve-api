package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.sse.SseEmitterService;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseEmitterService sseEmitterService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private NotificationMailer notificationMailer;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    private NotificationDispatcher dispatcher() {
        return new NotificationDispatcher(
                notificationRepository,
                sseEmitterService,
                new NotificationResponseMapper(
                        commentRepository,
                        problemRepository,
                        solutionRepository,
                        showCasesRepository,
                        userProfileRepository
                ),
                notificationMailer
        );
    }

    // ------------------------------------------------------------- the event

    @Test
    void theActorIsNeverNotifiedOfTheirOwnAction() {
        NotificationEvent event = NotificationEvent.toAllExcept(
                List.of(alice, bob),
                alice,
                "New comment",
                "hello",
                NotificationType.COMMENT,
                UUID.randomUUID(),
                "comment:1"
        );

        assertEquals(List.of(bob), event.recipientIds());
    }

    @Test
    void unresolvedRecipientsAreDropped() {
        // An author whose account is gone, an owner nobody could resolve.
        NotificationEvent event = new NotificationEvent(
                Arrays.asList(alice, null),
                "t", "c", NotificationType.COMMENT, UUID.randomUUID(), "k"
        );

        assertEquals(List.of(alice), event.recipientIds());
    }

    @Test
    void aRecipientListedTwiceIsNotifiedOnce() {
        // Somebody who is both the author and a follower.
        NotificationEvent event = new NotificationEvent(
                List.of(alice, alice),
                "t", "c", NotificationType.COMMENT, UUID.randomUUID(), "k"
        );

        assertEquals(List.of(alice), event.recipientIds());
        assertTrue(event.hasRecipients());
    }

    @Test
    void anEventWithNobodyLeftToTellIsInert() {
        NotificationEvent event = NotificationEvent.to(
                null, "t", "c", NotificationType.COMMENT, UUID.randomUUID(), "k"
        );

        assertFalse(event.hasRecipients());
    }

    // -------------------------------------------------------- the dispatcher

    @Test
    void redeliveringTheSameEventIsANoOpNotAConstraintViolation() {
        // (user_id, event_key) is unique, so without this check the second
        // delivery throws and takes its caller's transaction with it.
        when(notificationRepository.existsByUserIdAndEventKey(
                eq(alice), any()
        )).thenReturn(true);

        dispatcher().dispatch(
                alice,
                "Report triaged",
                "content",
                NotificationType.REPORT,
                UUID.randomUUID(),
                "report:1:state:resolved"
        );

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(sseEmitterService);
    }

    @Test
    void redeliveryStillReachesRecipientsWhoMissedItTheFirstTime() {
        UUID notifiableId = UUID.randomUUID();
        // Alice already got it; Bob did not, because the first delivery failed
        // partway. Skipping the whole batch would cost Bob his notification.
        when(notificationRepository.existsByUserIdAndEventKey(
                alice, "report:1:submitted:" + alice
        )).thenReturn(true);
        when(notificationRepository.existsByUserIdAndEventKey(
                bob, "report:1:submitted:" + bob
        )).thenReturn(false);
        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        dispatcher().dispatchToMany(
                List.of(alice, bob),
                "New report submitted",
                "content",
                NotificationType.REPORT,
                notifiableId,
                "report:1:submitted"
        );

        ArgumentCaptor<List<Notification>> saved =
                ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(saved.capture());

        assertEquals(1, saved.getValue().size());
        assertEquals(bob, saved.getValue().getFirst().getUserId());
    }

    @Test
    void aBatchWhereEveryoneAlreadyKnowsTouchesNothing() {
        when(notificationRepository.existsByUserIdAndEventKey(any(), any()))
                .thenReturn(true);

        dispatcher().dispatchToMany(
                List.of(alice, bob),
                "t", "c", NotificationType.REPORT, UUID.randomUUID(), "k"
        );

        verify(notificationRepository, never()).saveAll(anyList());
        verifyNoInteractions(sseEmitterService);
    }

    @Test
    void liveCommentNotificationIncludesTheAuthorsDisplayProfile() {
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setAuthorId(authorId);
        UserProfile profile = new UserProfile();
        profile.setId(authorId);
        profile.setFullName("Sok Dara");
        profile.setAvatarUrl("https://cdn.example.com/sok-dara.png");

        when(notificationRepository.existsByUserIdAndEventKey(any(), any()))
                .thenReturn(false);
        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(commentRepository.findAllById(List.of(commentId)))
                .thenReturn(List.of(comment));
        when(userProfileRepository.findAllById(List.of(authorId)))
                .thenReturn(List.of(profile));

        dispatcher().dispatchToMany(
                List.of(bob),
                "New comment",
                "hello",
                NotificationType.COMMENT,
                commentId,
                "comment:1"
        );

        ArgumentCaptor<NotificationResponse> response =
                ArgumentCaptor.forClass(NotificationResponse.class);
        verify(sseEmitterService).push(eq(bob), response.capture());
        assertEquals(authorId, response.getValue().authorId());
        assertEquals("Sok Dara", response.getValue().authorName());
        assertEquals(
                "https://cdn.example.com/sok-dara.png",
                response.getValue().authorAvatarUrl()
        );
    }

    // ---------------------------------------------------------- the listener

    @Test
    void deliveryFailureNeverEscapesTheListener() {
        NotificationDispatcher failing = dispatcher();
        when(notificationRepository.existsByUserIdAndEventKey(any(), any()))
                .thenReturn(false);
        when(notificationRepository.saveAll(anyList()))
                .thenThrow(new RuntimeException("database went away"));

        NotificationEventListener listener =
                new NotificationEventListener(failing);

        // The triage this announces is already committed. Rethrowing would
        // achieve nothing and there is no caller left to catch it.
        listener.onNotification(NotificationEvent.to(
                alice,
                "Report triaged",
                "content",
                NotificationType.REPORT,
                UUID.randomUUID(),
                "report:1:state:resolved"
        ));
    }

    @Test
    void anEventWithNoRecipientsNeverReachesTheDatabase() {
        NotificationEventListener listener =
                new NotificationEventListener(dispatcher());

        listener.onNotification(NotificationEvent.to(
                null, "t", "c", NotificationType.REPORT, UUID.randomUUID(), "k"
        ));

        verifyNoInteractions(notificationRepository);
    }
}
