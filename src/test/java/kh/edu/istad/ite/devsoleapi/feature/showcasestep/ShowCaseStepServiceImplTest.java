package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionWorkflow;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowCaseStepServiceImplTest {

    @Mock
    private ShowCaseStepRepository showcaseStepRepository;

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private ShowcaseStepMapper showcaseStepMapper;

    @Mock
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @Mock
    private ShowcaseStepRevisionRepository
            showcaseStepRevisionRepository;

    @Mock
    private ShowcaseRevisionWorkflow showcaseRevisionWorkflow;

    private ShowCaseStepServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShowCaseStepServiceImpl(
                showcaseStepRepository,
                showCasesRepository,
                showcaseStepMapper,
                showcaseRevisionRepository,
                showcaseStepRevisionRepository,
                showcaseRevisionWorkflow
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAllowsOwnerAndPersistsStep() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        CreateShowcaseStepRequest request = new CreateShowcaseStepRequest(
                1,
                "Create the project",
                "Initialize the application",
                null,
                null,
                null
        );
        ShowCases showcase = showcase(showcaseId, ownerId);
        ShowcaseStep step = new ShowcaseStep();
        ShowcaseStepResponse expected = ShowcaseStepResponse.builder()
                .stepNumber(1)
                .title("Create the project")
                .build();

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseStepRepository
                .existsByShowcase_IdAndStepNumber(showcaseId, 1))
                .thenReturn(false);
        when(showcaseStepMapper
                .mapCreateShowcaseStepRequestToShowcaseStep(request))
                .thenReturn(step);
        when(showcaseStepRepository.save(step)).thenReturn(step);
        when(showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(step))
                .thenReturn(expected);

        ShowcaseStepResponse actual = service.create(showcaseId, request);

        assertSame(expected, actual);
        assertSame(showcase, step.getShowcase());
        verify(showcaseStepRepository).save(step);
    }

    @Test
    void createRejectsNonOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        CreateShowcaseStepRequest request = new CreateShowcaseStepRequest(
                1,
                "Create the project",
                "Initialize the application",
                null,
                null,
                null
        );

        authenticate(currentUserId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase(showcaseId, ownerId)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(showcaseId, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(showcaseStepRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(showcaseStepMapper);
    }

    @Test
    void createRejectsDuplicateStepNumber() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        CreateShowcaseStepRequest request = new CreateShowcaseStepRequest(
                1,
                "Duplicate step",
                "Duplicate order",
                null,
                null,
                null
        );

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase(showcaseId, ownerId)));
        when(showcaseStepRepository
                .existsByShowcase_IdAndStepNumber(showcaseId, 1))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(showcaseId, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(showcaseStepRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
        verifyNoInteractions(showcaseStepMapper);
    }

    @Test
    void updateRejectsStepNumberUsedByAnotherStep() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        ShowCases showcase = showcase(showcaseId, ownerId);
        ShowcaseStep step = new ShowcaseStep();
        step.setId(stepId);
        step.setShowcase(showcase);
        step.setStepNumber(1);
        UpdateShowcaseStepRequest request = new UpdateShowcaseStepRequest(
                2,
                null,
                null,
                null,
                null,
                null
        );

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseStepRepository
                .findByIdAndShowcase_IdAndShowcase_DeletedAtIsNull(
                        stepId,
                        showcaseId
                ))
                .thenReturn(Optional.of(step));
        when(showcaseStepRepository
                .existsByShowcase_IdAndStepNumberAndIdNot(
                        showcaseId,
                        2,
                        stepId
                ))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(showcaseId, stepId, request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(1, step.getStepNumber());
        verify(showcaseStepRepository, never()).save(step);
    }

    @Test
    void malformedAuthenticatedUserIdReturnsUnauthorized() {
        authenticate("not-a-uuid");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(UUID.randomUUID(), UUID.randomUUID())
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(
                showcaseStepRepository,
                showCasesRepository,
                showcaseStepMapper,
                showcaseRevisionRepository,
                showcaseStepRevisionRepository,
                showcaseRevisionWorkflow
        );
    }

    @Test
    void updateApprovedShowcaseChangesOnlyCandidateStep() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UUID publishedStepId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        ShowCases showcase = showcase(showcaseId, ownerId);
        showcase.setReviewStatus(ReviewStatus.APPROVED);

        ShowcaseRevision revision = new ShowcaseRevision();
        revision.setId(revisionId);
        revision.setShowcase(showcase);
        ShowcaseStepRevision candidate =
                new ShowcaseStepRevision();
        candidate.setId(UUID.randomUUID());
        candidate.setRevision(revision);
        candidate.setSourceStepId(publishedStepId);
        candidate.setStepNumber(1);
        candidate.setTitle("Published step");

        UpdateShowcaseStepRequest request =
                new UpdateShowcaseStepRequest(
                        2,
                        "Candidate step",
                        null,
                        null,
                        null,
                        null
                );
        ShowcaseStepResponse expected =
                ShowcaseStepResponse.builder()
                        .id(publishedStepId)
                        .stepNumber(2)
                        .title("Candidate step")
                        .build();

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionWorkflow.getOrCreate(
                showcase,
                ownerId
        )).thenReturn(revision);
        when(showcaseStepRevisionRepository
                .findByIdAndRevision_Showcase_Id(
                        publishedStepId,
                        showcaseId
                )).thenReturn(Optional.empty());
        when(showcaseStepRevisionRepository
                .findBySourceStepIdAndRevision_Showcase_Id(
                        publishedStepId,
                        showcaseId
                )).thenReturn(Optional.of(candidate));
        when(showcaseStepRevisionRepository
                .existsByRevision_IdAndStepNumberAndIdNot(
                        revisionId,
                        2,
                        candidate.getId()
                )).thenReturn(false);
        when(showcaseStepRevisionRepository.save(candidate))
                .thenReturn(candidate);
        when(showcaseStepMapper
                .mapShowcaseStepRevisionToShowcaseStepResponse(
                        candidate
                )).thenReturn(expected);

        ShowcaseStepResponse actual = service.update(
                showcaseId,
                publishedStepId,
                request
        );

        assertSame(expected, actual);
        assertEquals(2, candidate.getStepNumber());
        assertEquals("Candidate step", candidate.getTitle());
        verify(showcaseRevisionWorkflow).submit(
                revision,
                ownerId
        );
        verify(showcaseStepRepository, never()).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    private ShowCases showcase(UUID showcaseId, UUID ownerId) {
        UserProfile owner = new UserProfile();
        owner.setId(ownerId);

        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);
        showcase.setAuthor(owner);
        showcase.setReviewStatus(ReviewStatus.PENDING);
        return showcase;
    }

    private void authenticate(String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
