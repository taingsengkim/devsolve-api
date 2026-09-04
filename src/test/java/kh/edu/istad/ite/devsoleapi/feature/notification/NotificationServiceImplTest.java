package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.Solution;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inboxIsAlwaysScopedToAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(true),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).getMine(true, 0, 20).getContent().getFirst();

        assertEquals(notification.getId(), response.id());
        assertEquals(NotificationType.PROBLEM, response.notifiableType());
    }

    @Test
    void markReadCannotEscapeAuthenticatedUsersInbox() {
        UUID userId = UUID.randomUUID();
        Notification notification = notification(userId);
        authenticate(userId);
        when(notificationRepository.findByIdAndUserId(
                notification.getId(),
                userId
        )).thenReturn(Optional.of(notification));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).markRead(notification.getId());

        assertTrue(response.read());
        verify(notificationRepository).markRead(
                notification.getId(),
                userId
        );
    }

    @Test
    void commentNotificationIncludesItsAuthorsDisplayProfile() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Notification notification = notification(userId);
        notification.setNotifiableType(NotificationType.COMMENT);
        notification.setNotifiableId(commentId);

        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setAuthorId(authorId);
        UserProfile profile = new UserProfile();
        profile.setId(authorId);
        profile.setFullName("Sok Dara");
        profile.setAvatarUrl("https://cdn.example.com/sok-dara.png");

        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));
        when(commentRepository.findAllById(List.of(commentId)))
                .thenReturn(List.of(comment));
        when(userProfileRepository.findAllById(List.of(authorId)))
                .thenReturn(List.of(profile));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).getMine(false, 0, 20).getContent().getFirst();

        assertEquals(authorId, response.authorId());
        assertEquals("Sok Dara", response.authorName());
        assertEquals(
                "https://cdn.example.com/sok-dara.png",
                response.authorAvatarUrl()
        );
    }

    @Test
    void solutionNotificationNamesWhoWroteTheSolution() {
        UUID userId = UUID.randomUUID();
        UUID solutionId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Notification notification = notification(userId);
        notification.setNotifiableType(NotificationType.SOLUTION);
        notification.setNotifiableId(solutionId);

        Solution solution = Solution.builder()
                .id(solutionId)
                .authorId(authorId)
                .build();
        UserProfile profile = new UserProfile();
        profile.setId(authorId);
        profile.setUsername("sok.dara");
        profile.setFullName("Sok Dara");
        profile.setAvatarUrl("https://cdn.example.com/sok-dara.png");

        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));
        when(solutionRepository.findAllById(List.of(solutionId)))
                .thenReturn(List.of(solution));
        when(userProfileRepository.findAllById(List.of(authorId)))
                .thenReturn(List.of(profile));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).getMine(false, 0, 20).getContent().getFirst();

        assertEquals(authorId, response.authorId());
        assertEquals("sok.dara", response.authorUsername());
        assertEquals("Sok Dara", response.authorName());
        assertEquals(
                "https://cdn.example.com/sok-dara.png",
                response.authorAvatarUrl()
        );
    }

    /**
     * "Your problem was published" points at the reader's own problem. There is
     * no person behind it — the platform decided — so it must not come back
     * wearing the reader's face.
     */
    @Test
    void aNotificationAboutOnesOwnContentStaysFaceless() {
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        Notification notification = notification(userId);
        notification.setNotifiableType(NotificationType.PROBLEM);
        notification.setNotifiableId(problemId);

        Problem problem = Problem.builder()
                .id(problemId)
                .authorId(userId)
                .build();

        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));
        when(problemRepository.findAllById(List.of(problemId)))
                .thenReturn(List.of(problem));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).getMine(false, 0, 20).getContent().getFirst();

        assertNull(response.authorId());
        assertNull(response.authorName());
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void removedCommentNotificationDoesNotReidentifyItsAuthor() {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Notification notification = notification(userId);
        notification.setNotifiableType(NotificationType.COMMENT);
        notification.setNotifiableId(commentId);

        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setAuthorId(UUID.randomUUID());
        comment.setRemovedAt(LocalDateTime.now());

        authenticate(userId);
        when(notificationRepository.findInbox(
                eq(userId),
                eq(false),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(notification)));
        when(commentRepository.findAllById(List.of(commentId)))
                .thenReturn(List.of(comment));

        NotificationResponse response = new NotificationServiceImpl(
                notificationRepository,
                responseMapper(),
                preferenceRepository
        ).getMine(false, 0, 20).getContent().getFirst();

        assertNull(response.authorId());
        assertNull(response.authorName());
        assertNull(response.authorAvatarUrl());
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void nonCommentJsonDoesNotAddEmptyAuthorFields() throws Exception {
        NotificationResponse response = responseMapper().toResponse(
                notification(UUID.randomUUID())
        );

        String json = new ObjectMapper().writeValueAsString(response);

        assertFalse(json.contains("\"authorId\""));
        assertFalse(json.contains("\"authorName\""));
        assertFalse(json.contains("\"authorAvatarUrl\""));
    }

    private NotificationResponseMapper responseMapper() {
        return new NotificationResponseMapper(
                commentRepository,
                problemRepository,
                solutionRepository,
                showCasesRepository,
                userProfileRepository
        );
    }

    private Notification notification(UUID userId) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("New problem published")
                .content("How to secure a REST API")
                .notifiableType(NotificationType.PROBLEM)
                .notifiableId(UUID.randomUUID())
                .eventKey("problem-published:" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();
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
