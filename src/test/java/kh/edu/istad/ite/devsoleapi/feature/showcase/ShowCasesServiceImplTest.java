package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewQueueItemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStep;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepMapper;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowCasesServiceImplTest {

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ShowCasesMapper showCasesMapper;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @Mock
    private ShowCaseStepRepository showcaseStepRepository;

    @Mock
    private ShowcaseStepMapper showcaseStepMapper;

    @Mock
    private ShowcaseRevisionWorkflow showcaseRevisionWorkflow;

    @Mock
    private ShowcaseReviewHistoryRepository
            showcaseReviewHistoryRepository;
    @Mock
    private FollowNotificationService followNotificationService;

    private ShowCasesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShowCasesServiceImpl(
                showCasesRepository,
                userProfileRepository,
                showCasesMapper,
                categoryRepository,
                showcaseRevisionRepository,
                showcaseStepRepository,
                showcaseStepMapper,
                showcaseRevisionWorkflow,
                showcaseReviewHistoryRepository,
                followNotificationService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyShowcasesReturnsAllNonDeletedShowcasesNewestFirst() {
        UUID authorId = UUID.randomUUID();
        ShowCases showcase = new ShowCases();
        showcase.setId(UUID.randomUUID());
        showcase.setReviewStatus(ReviewStatus.PENDING);
        ShowCasesSummaryResponse response = ShowCasesSummaryResponse.builder()
                .id(showcase.getId())
                .reviewStatus(ReviewStatus.PENDING)
                .build();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        authenticate(authorId.toString());
        when(showCasesRepository.findByAuthor_IdAndDeletedAtIsNull(
                eq(authorId),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(showcase)));
        when(showcaseRevisionRepository.findByShowcase_IdIn(
                List.of(showcase.getId())
        )).thenReturn(List.of());
        when(showCasesMapper.mapShowCaseToSummaryResponse(showcase))
                .thenReturn(response);

        Page<ShowCasesSummaryResponse> result =
                service.getMyShowcases(1, 10);

        assertEquals(List.of(response), result.getContent());
        verify(showCasesRepository)
                .findByAuthor_IdAndDeletedAtIsNull(
                        eq(authorId),
                        pageableCaptor.capture()
                );

        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection()
        );
    }

    @Test
    void getMyShowcasesRejectsMalformedAuthenticatedUserId() {
        authenticate("not-a-uuid");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getMyShowcases(0, 20)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(
                showCasesRepository,
                userProfileRepository,
                showCasesMapper,
                categoryRepository,
                showcaseRevisionRepository,
                showcaseStepRepository,
                showcaseStepMapper,
                showcaseRevisionWorkflow,
                showcaseReviewHistoryRepository
        );
    }

    @Test
    void getAllPublishedAppliesSearchFilterAndWhitelistedSort() {
        UUID categoryId = UUID.randomUUID();
        ShowCases showcase = new ShowCases();
        showcase.setId(UUID.randomUUID());
        ShowCasesResponse response = ShowCasesResponse.builder()
                .id(showcase.getId())
                .build();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(showCasesRepository.searchPublished(
                eq(ReviewStatus.APPROVED),
                eq("traffic"),
                eq(categoryId),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(showcase)));
        when(showCasesMapper.mapShowCaseToShowCaseResponse(showcase))
                .thenReturn(response);

        Page<ShowCasesResponse> result =
                service.getAllPublished(
                        "  traffic  ",
                        categoryId,
                        "viewCount",
                        "asc",
                        1,
                        10
                );

        assertEquals(List.of(response), result.getContent());
        verify(showCasesRepository).searchPublished(
                eq(ReviewStatus.APPROVED),
                eq("traffic"),
                eq(categoryId),
                pageableCaptor.capture()
        );
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageable.getSort()
                        .getOrderFor("viewCount")
                        .getDirection()
        );
    }

    @Test
    void getByIdReturnsApprovedShowcaseWithOrderedSteps() {
        UUID showcaseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);

        ShowcaseStep firstStep = new ShowcaseStep();
        firstStep.setId(UUID.randomUUID());
        firstStep.setStepNumber(1);
        ShowcaseStep secondStep = new ShowcaseStep();
        secondStep.setId(UUID.randomUUID());
        secondStep.setStepNumber(2);

        ShowcaseStepResponse firstResponse =
                ShowcaseStepResponse.builder()
                        .id(firstStep.getId())
                        .stepNumber(1)
                        .build();
        ShowcaseStepResponse secondResponse =
                ShowcaseStepResponse.builder()
                        .id(secondStep.getId())
                        .stepNumber(2)
                        .build();
        List<ShowcaseStepResponse> stepResponses =
                List.of(firstResponse, secondResponse);
        ShowCasesResponse expected = ShowCasesResponse.builder()
                .id(showcaseId)
                .title("Published title")
                .steps(stepResponses)
                .build();

        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcaseId))
                .thenReturn(List.of(firstStep, secondStep));
        when(showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(firstStep))
                .thenReturn(firstResponse);
        when(showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(secondStep))
                .thenReturn(secondResponse);
        when(showCasesMapper.mapShowCaseToDetailResponse(
                showcase,
                stepResponses
        )).thenReturn(expected);

        ShowCasesResponse actual = service.getById(showcaseId);

        assertSame(expected, actual);
        assertEquals(stepResponses, actual.steps());
    }

    @Test
    void updateApprovedShowcaseCreatesPendingRevision() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        UpdateShowCasesRequest request = new UpdateShowCasesRequest(
                null,
                "Improved title",
                "Improved overview",
                null,
                null,
                null,
                null
        );
        ShowCasesResponse expected = ShowCasesResponse.builder()
                .id(showcaseId)
                .title("Improved title")
                .reviewStatus(ReviewStatus.PENDING)
                .build();

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Published title"
        );
        when(showcaseRevisionWorkflow.getOrCreate(
                showcase,
                ownerId
        )).thenReturn(revision);
        when(showCasesMapper.mapRevisionToShowCaseResponse(
                revision
        )).thenReturn(expected);

        ShowCasesResponse actual = service.update(showcaseId, request);

        assertSame(expected, actual);
        assertEquals("Published title", showcase.getTitle());
        assertEquals(ReviewStatus.APPROVED, showcase.getReviewStatus());

        assertEquals("Improved title", revision.getTitle());
        assertEquals("Improved overview", revision.getOverview());
        verify(showcaseRevisionWorkflow).submit(revision, ownerId);
        verify(showCasesRepository, never()).save(showcase);
    }

    @Test
    void approvingRevisionPromotesItAndRemovesDraft() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Improved title"
        );
        ShowCasesResponse expected = ShowCasesResponse.builder()
                .id(showcaseId)
                .title("Improved title")
                .reviewStatus(ReviewStatus.APPROVED)
                .build();

        authenticate(adminId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.of(revision));
        when(showCasesRepository.save(showcase)).thenReturn(showcase);
        when(showCasesMapper.mapShowCaseToShowCaseResponse(showcase))
                .thenReturn(expected);

        ShowCasesResponse actual = service.updateStatus(
                showcaseId,
                new UpdateShowcaseStatusRequest(
                        ReviewStatus.APPROVED,
                        null
                )
        );

        assertSame(expected, actual);
        assertEquals("Improved title", showcase.getTitle());
        assertEquals(ReviewStatus.APPROVED, showcase.getReviewStatus());
        assertEquals(adminId, showcase.getReviewedBy());
        verify(showcaseRevisionWorkflow).promoteSteps(
                showcase,
                revision
        );
        verify(showcaseRevisionWorkflow).discard(revision);

        ArgumentCaptor<ShowcaseReviewHistory> historyCaptor =
                ArgumentCaptor.forClass(
                        ShowcaseReviewHistory.class
                );
        verify(showcaseReviewHistoryRepository)
                .save(historyCaptor.capture());
        ShowcaseReviewHistory history =
                historyCaptor.getValue();
        assertEquals(
                ShowcaseSubmissionType.REVISION,
                history.getSubmissionType()
        );
        assertEquals(
                ReviewStatus.APPROVED,
                history.getReviewStatus()
        );
        assertEquals(adminId, history.getReviewedBy());
    }

    @Test
    void rejectingRevisionKeepsPublishedShowcaseVisible() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Rejected title"
        );
        ShowCasesResponse expected = ShowCasesResponse.builder()
                .id(showcaseId)
                .title("Rejected title")
                .reviewStatus(ReviewStatus.REJECTED)
                .build();

        authenticate(adminId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.of(revision));
        when(showcaseRevisionRepository.save(revision))
                .thenReturn(revision);
        when(showCasesMapper.mapRevisionToShowCaseResponse(revision))
                .thenReturn(expected);

        ShowCasesResponse actual = service.updateStatus(
                showcaseId,
                new UpdateShowcaseStatusRequest(
                        ReviewStatus.REJECTED,
                        "The live URL could not be verified."
                )
        );

        assertSame(expected, actual);
        assertEquals("Published title", showcase.getTitle());
        assertEquals(ReviewStatus.APPROVED, showcase.getReviewStatus());
        assertEquals(ReviewStatus.REJECTED, revision.getReviewStatus());
        assertEquals(
                "The live URL could not be verified.",
                revision.getRejectionReason()
        );
        verify(showCasesRepository, never()).save(showcase);
        verify(showcaseRevisionWorkflow, never())
                .promoteSteps(showcase, revision);
        verify(showcaseRevisionWorkflow, never()).discard(revision);

        ArgumentCaptor<ShowcaseReviewHistory> historyCaptor =
                ArgumentCaptor.forClass(
                        ShowcaseReviewHistory.class
                );
        verify(showcaseReviewHistoryRepository)
                .save(historyCaptor.capture());
        assertEquals(
                ReviewStatus.REJECTED,
                historyCaptor.getValue().getReviewStatus()
        );
        assertEquals(
                "The live URL could not be verified.",
                historyCaptor.getValue().getRejectionReason()
        );
    }

    @Test
    void getReviewDetailReturnsRevisionMetadataAndCandidateSteps() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Candidate title"
        );
        revision.setId(revisionId);

        ShowcaseStepRevision candidateStep =
                new ShowcaseStepRevision();
        candidateStep.setId(UUID.randomUUID());
        candidateStep.setStepNumber(1);
        ShowcaseStepResponse stepResponse =
                ShowcaseStepResponse.builder()
                        .id(candidateStep.getId())
                        .stepNumber(1)
                        .build();
        ShowcaseReviewDetailResponse expected =
                ShowcaseReviewDetailResponse.builder()
                        .showcaseId(showcaseId)
                        .revisionId(revisionId)
                        .submissionType(
                                ShowcaseSubmissionType.REVISION
                        )
                        .steps(List.of(stepResponse))
                        .build();

        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.of(revision));
        when(showcaseRevisionWorkflow.getCandidateSteps(revisionId))
                .thenReturn(List.of(candidateStep));
        when(showcaseStepMapper
                .mapShowcaseStepRevisionToShowcaseStepResponse(
                        candidateStep
                ))
                .thenReturn(stepResponse);
        when(showCasesMapper.mapRevisionToReviewDetail(
                revision,
                List.of(stepResponse)
        )).thenReturn(expected);

        ShowcaseReviewDetailResponse actual =
                service.getReviewDetail(showcaseId);

        assertSame(expected, actual);
        assertEquals(List.of(stepResponse), actual.steps());
    }

    @Test
    void getMyRevisionReturnsOnlyOwnersCandidate() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Candidate title"
        );
        revision.setId(revisionId);
        ShowcaseReviewDetailResponse expected =
                ShowcaseReviewDetailResponse.builder()
                        .showcaseId(showcaseId)
                        .revisionId(revisionId)
                        .steps(List.of())
                        .build();

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.of(revision));
        when(showcaseRevisionWorkflow.getCandidateSteps(revisionId))
                .thenReturn(List.of());
        when(showCasesMapper.mapRevisionToReviewDetail(
                revision,
                List.of()
        )).thenReturn(expected);

        ShowcaseReviewDetailResponse actual =
                service.getMyRevision(showcaseId);

        assertSame(expected, actual);
    }

    @Test
    void cancelRevisionDeletesDraftButKeepsPublishedShowcase() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Published title"
        );
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        ShowcaseRevision revision = revision(
                showcase,
                ownerId,
                "Candidate title"
        );

        authenticate(ownerId.toString());
        when(showCasesRepository.findByIdAndDeletedAtIsNull(showcaseId))
                .thenReturn(Optional.of(showcase));
        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.of(revision));

        service.cancelRevision(showcaseId);

        verify(showcaseRevisionWorkflow).discard(revision);
        verify(showCasesRepository, never()).delete(showcase);
    }

    @Test
    void restoreMakesSoftDeletedShowcaseActiveAgain() {
        UUID ownerId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = showcase(
                showcaseId,
                ownerId,
                "Deleted showcase"
        );
        showcase.setDeletedAt(LocalDateTime.now());

        authenticate(ownerId.toString());
        when(showCasesRepository.findById(showcaseId))
                .thenReturn(Optional.of(showcase));

        service.restore(showcaseId);

        assertNull(showcase.getDeletedAt());
        verify(showCasesRepository).save(showcase);
    }

    @Test
    void incrementViewCountUsesAtomicRepositoryUpdate() {
        UUID showcaseId = UUID.randomUUID();
        when(showCasesRepository.incrementViewCount(
                showcaseId,
                ReviewStatus.APPROVED
        )).thenReturn(1);
        when(showCasesRepository.findViewCountById(showcaseId))
                .thenReturn(42);

        ShowcaseViewCountResponse response =
                service.incrementViewCount(showcaseId);

        assertEquals(showcaseId, response.showcaseId());
        assertEquals(42, response.viewCount());
    }

    @Test
    void getReviewHistoryReturnsNewestDecisionsFirst() {
        UUID showcaseId = UUID.randomUUID();
        ShowcaseReviewHistory history =
                new ShowcaseReviewHistory();
        history.setId(UUID.randomUUID());
        ShowcaseReviewHistoryResponse response =
                ShowcaseReviewHistoryResponse.builder()
                        .id(history.getId())
                        .showcaseId(showcaseId)
                        .build();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(showcaseReviewHistoryRepository
                .findByShowcaseIdOrderByReviewedAtDesc(
                        eq(showcaseId),
                        any(Pageable.class)
                ))
                .thenReturn(new PageImpl<>(List.of(history)));
        when(showCasesMapper.mapReviewHistory(history))
                .thenReturn(response);

        Page<ShowcaseReviewHistoryResponse> result =
                service.getReviewHistory(showcaseId, 0, 10);

        assertEquals(List.of(response), result.getContent());
        verify(showcaseReviewHistoryRepository)
                .findByShowcaseIdOrderByReviewedAtDesc(
                        eq(showcaseId),
                        pageableCaptor.capture()
                );
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getReviewQueueMapsPendingRevisionAndPagination() {
        UUID showcaseId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        LocalDateTime submittedAt = LocalDateTime.now();
        ShowcaseReviewQueueProjection projection =
                mock(ShowcaseReviewQueueProjection.class);
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        when(projection.getShowcaseId()).thenReturn(showcaseId);
        when(projection.getRevisionId()).thenReturn(revisionId);
        when(projection.getSubmissionType()).thenReturn("REVISION");
        when(projection.getAuthorId()).thenReturn(authorId);
        when(projection.getAuthorName()).thenReturn("Sokha Chan");
        when(projection.getTitle()).thenReturn("Updated showcase");
        when(projection.getReviewStatus())
                .thenReturn("pending_approval");
        when(projection.getSubmittedAt()).thenReturn(submittedAt);
        when(showCasesRepository.findReviewQueue(
                eq("PENDING"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(projection)));

        Page<ShowcaseReviewQueueItemResponse> result =
                service.getReviewQueue(ReviewStatus.PENDING, 2, 15);

        ShowcaseReviewQueueItemResponse item =
                result.getContent().getFirst();
        assertEquals(showcaseId, item.showcaseId());
        assertEquals(revisionId, item.revisionId());
        assertEquals(
                ShowcaseSubmissionType.REVISION,
                item.submissionType()
        );
        assertEquals(ReviewStatus.PENDING, item.reviewStatus());
        assertEquals(submittedAt, item.submittedAt());

        verify(showCasesRepository).findReviewQueue(
                eq("PENDING"),
                pageableCaptor.capture()
        );
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(15, pageableCaptor.getValue().getPageSize());
    }

    private ShowCases showcase(
            UUID showcaseId,
            UUID ownerId,
            String title
    ) {
        UserProfile owner = new UserProfile();
        owner.setId(ownerId);

        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);
        showcase.setAuthor(owner);
        showcase.setTitle(title);
        showcase.setOverview("Published overview");
        return showcase;
    }

    private ShowcaseRevision revision(
            ShowCases showcase,
            UUID submittedBy,
            String title
    ) {
        ShowcaseRevision revision = new ShowcaseRevision();
        revision.setShowcase(showcase);
        revision.setCategory(showcase.getCategory());
        revision.setTitle(title);
        revision.setOverview("Updated overview");
        revision.setReviewStatus(ReviewStatus.PENDING);
        revision.setSubmittedBy(submittedBy);
        return revision;
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
