package kh.edu.istad.ite.devsoleapi.feature.moderation.takedown;

import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationAction;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionMapper;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionRepository;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesService;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One door in front of five removals, and an audit row behind them.
 *
 * <p>The removals themselves belong to the features that own them and are
 * tested there. What is worth pinning here is the dispatch — sending a showcase
 * takedown to the problem service would delete the wrong person's work — and
 * the ordering: the record must not survive a removal that was refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentTakedownServiceImplTest {

    @Mock
    private ProblemService problemService;
    @Mock
    private ShowCasesService showCasesService;
    @Mock
    private SolutionService solutionService;
    @Mock
    private CommentService commentService;
    @Mock
    private ProgramService programService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ModerationActionRepository moderationActionRepository;
    @Mock
    private ModerationActionMapper moderationActionMapper;

    private ContentTakedownServiceImpl service;

    private UUID adminId;

    @BeforeEach
    void setUp() {
        service = new ContentTakedownServiceImpl(
                problemService,
                showCasesService,
                solutionService,
                commentService,
                programService,
                userProfileRepository,
                moderationActionRepository,
                moderationActionMapper
        );

        adminId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(moderationActionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aProblemTakedownGoesToTheProblemServiceAndNowhereElse() {
        authenticate(adminId, true);
        UUID problemId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.PROBLEM,
                problemId,
                "Contains another user's personal data."
        );

        verify(problemService).softDelete(problemId);
        verifyNoInteractions(
                showCasesService,
                solutionService,
                commentService,
                programService
        );
    }

    @Test
    void aShowcaseTakedownGoesToTheAdminPathRatherThanTheAuthorsOwnDelete() {
        authenticate(adminId, true);
        UUID showcaseId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.SHOWCASE,
                showcaseId,
                "Weaponised proof of concept."
        );

        // Not softDelete: that one refuses anybody but the author, so routing a
        // takedown through it would 403 on every showcase an admin did not
        // write themselves.
        verify(showCasesService).removeByAdmin(showcaseId);
        verify(showCasesService, never()).softDelete(any());
    }

    @Test
    void aSolutionTakedownGoesToTheSolutionService() {
        authenticate(adminId, true);
        UUID solutionId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.SOLUTION,
                solutionId,
                "Plagiarised."
        );

        verify(solutionService).deleteSolution(solutionId);
    }

    /**
     * The moderator's id has to travel with a comment removal — it is what
     * stamps the tombstone as a moderator's decision rather than the author's.
     */
    @Test
    void aCommentTakedownCarriesTheModeratorWhoOrderedIt() {
        authenticate(adminId, true);
        UUID commentId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.COMMENT,
                commentId,
                "Targeted harassment."
        );

        verify(commentService).removeByModerator(commentId, adminId);
    }

    @Test
    void aProgramTakedownGoesToTheProgramService() {
        authenticate(adminId, true);
        UUID programId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.PROGRAM,
                programId,
                "Scope invites attacks on third parties."
        );

        verify(programService).removeProgramByAdmin(programId);
    }

    @Test
    void theRecordNamesTheContentTheAdminAndWhy() {
        authenticate(adminId, true);
        UUID showcaseId = UUID.randomUUID();

        service.takeDown(
                ModerationTargetType.SHOWCASE,
                showcaseId,
                "  Weaponised proof of concept.  "
        );

        ArgumentCaptor<ModerationAction> captor =
                ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(captor.capture());
        ModerationAction action = captor.getValue();

        // The six content target types existed on the enum for a long time
        // with nothing ever writing them; this is the whole point of the class.
        assertEquals(ModerationTargetType.SHOWCASE, action.getTargetType());
        assertEquals(showcaseId, action.getTargetId());
        assertEquals(ModerationActionType.REMOVE, action.getAction());
        assertEquals(adminId, action.getAdmin().getId());
        assertEquals("Weaponised proof of concept.", action.getReason());
    }

    /**
     * A takedown that was refused deeper in — content already gone, or a
     * solution whose acceptance blocks it — must leave no record claiming it
     * happened. The two share a transaction, so the throw is what protects it;
     * this pins the ordering that makes the throw effective.
     */
    @Test
    void aRefusedRemovalWritesNoRecord() {
        authenticate(adminId, true);
        UUID problemId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(problemService).softDelete(problemId);

        assertThrows(
                ResponseStatusException.class,
                () -> service.takeDown(
                        ModerationTargetType.PROBLEM,
                        problemId,
                        "Already gone."
                )
        );

        verify(moderationActionRepository, never()).save(any());
    }

    @Test
    void aNonAdminIsRefusedBeforeAnythingIsTouched() {
        authenticate(adminId, false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.takeDown(
                        ModerationTargetType.PROBLEM,
                        UUID.randomUUID(),
                        "Nice try."
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(problemService, moderationActionRepository);
    }

    /**
     * An account is moderated through {@code ModerationService}, which also
     * has to disable the Keycloak login; a report is evidence in a disclosure
     * timeline. Neither is a post, and neither has a removal path here.
     */
    @ParameterizedTest
    @EnumSource(
            value = ModerationTargetType.class,
            names = {"USER", "REPORT"}
    )
    void aTargetWithNoTakedownPathIsRefused(ModerationTargetType targetType) {
        authenticate(adminId, true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.takeDown(
                        targetType,
                        UUID.randomUUID(),
                        "Wrong door."
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(moderationActionRepository, never()).save(any());
    }

    /**
     * Every content type the enum admits is dispatched somewhere. A value added
     * later without a branch would fall through and record a takedown that
     * removed nothing.
     */
    @ParameterizedTest
    @EnumSource(
            value = ModerationTargetType.class,
            names = {"PROBLEM", "SHOWCASE", "SOLUTION", "COMMENT", "PROGRAM"}
    )
    void everyContentTypeIsRemovableAndRecorded(
            ModerationTargetType targetType
    ) {
        authenticate(adminId, true);
        UUID targetId = UUID.randomUUID();

        service.takeDown(targetType, targetId, "Policy breach.");

        ArgumentCaptor<ModerationAction> captor =
                ArgumentCaptor.forClass(ModerationAction.class);
        verify(moderationActionRepository).save(captor.capture());
        assertSame(targetType, captor.getValue().getTargetType());
        assertEquals(targetId, captor.getValue().getTargetId());
    }

    private void authenticate(UUID subject, boolean admin) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        List<SimpleGrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities)
        );
    }
}
