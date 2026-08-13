package kh.edu.istad.ite.devsoleapi.feature.comments;

import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CreateCommentRequest;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportDiscussionAccess;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The internal comment split, which is the part of report notifications worth
 * pinning down: an internal note is the organization talking among themselves
 * about a researcher's report, and a notification that reached the researcher
 * would leak both that it happened and what it said.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportCommentNotificationTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ReportService reportService;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ShowCasesRepository showCasesRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private OrganizationAuthorizationService organizationAuthorization;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CommentServiceImpl service;

    private final UUID reportId = UUID.randomUUID();
    private final UUID reporterId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID triagerId = UUID.randomUUID();
    private final UUID otherTriagerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommentServiceImpl(
                commentRepository,
                reportService,
                problemRepository,
                solutionRepository,
                programRepository,
                showCasesRepository,
                userProfileRepository,
                organizationAuthorization,
                eventPublisher
        );

        when(commentRepository.saveAndFlush(any(Comment.class)))
                .thenAnswer(invocation -> {
                    Comment saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(LocalDateTime.now());
                    saved.setUpdatedAt(LocalDateTime.now());
                    return saved;
                });
        when(commentRepository.countActiveReplies(any(), anyBoolean()))
                .thenReturn(List.of());
        when(organizationAuthorization.findUserIdsWithPermission(
                organizationId,
                OrganizationPermission.VIEW_REPORTS
        )).thenReturn(Set.of(triagerId, otherTriagerId));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void triagerCanSeeInternal() {
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(
                        true, true, true, reporterId, organizationId
                ));
    }

    private List<NotificationEvent> publishedEvents() {
        ArgumentCaptor<Object> captor =
                ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues().stream()
                .filter(NotificationEvent.class::isInstance)
                .map(NotificationEvent.class::cast)
                .toList();
    }

    @Test
    void internalNoteNeverReachesTheReporter() {
        authenticate(triagerId);
        triagerCanSeeInternal();

        service.create(new CreateCommentRequest(
                CommentableType.REPORT,
                reportId,
                "Looks like a duplicate of the one we fixed last week.",
                null,
                true
        ));

        for (NotificationEvent event : publishedEvents()) {
            assertFalse(
                    event.recipientIds().contains(reporterId),
                    "internal note leaked to the reporter"
            );
        }
    }

    @Test
    void internalNoteStillReachesTheRestOfTheOrganization() {
        authenticate(triagerId);
        triagerCanSeeInternal();

        service.create(new CreateCommentRequest(
                CommentableType.REPORT,
                reportId,
                "Internal note",
                null,
                true
        ));

        List<UUID> notified = publishedEvents().stream()
                .flatMap(event -> event.recipientIds().stream())
                .toList();

        assertTrue(notified.contains(otherTriagerId));
        // ...but not the person who wrote it
        assertFalse(notified.contains(triagerId));
    }

    @Test
    void publicCommentReachesBothTheReporterAndTheOrganization() {
        authenticate(triagerId);
        triagerCanSeeInternal();

        service.create(new CreateCommentRequest(
                CommentableType.REPORT,
                reportId,
                "Could you share the request headers?",
                null,
                false
        ));

        List<UUID> notified = publishedEvents().stream()
                .flatMap(event -> event.recipientIds().stream())
                .toList();

        assertTrue(notified.contains(reporterId));
        assertTrue(notified.contains(otherTriagerId));
    }

    @Test
    void reporterCommentingIsNotNotifiedOfTheirOwnComment() {
        authenticate(reporterId);
        when(reportService.requireDiscussionAccess(reportId))
                .thenReturn(new ReportDiscussionAccess(
                        false, true, false, reporterId, organizationId
                ));

        service.create(new CreateCommentRequest(
                CommentableType.REPORT,
                reportId,
                "Here are the headers.",
                null,
                false
        ));

        List<UUID> notified = publishedEvents().stream()
                .flatMap(event -> event.recipientIds().stream())
                .toList();

        assertFalse(notified.contains(reporterId));
        assertEquals(2, notified.size());
        assertTrue(notified.contains(triagerId));
        assertTrue(notified.contains(otherTriagerId));
    }

    private void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
