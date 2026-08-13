package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemTechnologyRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagId;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagResolver;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import kh.edu.istad.ite.devsoleapi.common.listing.ViewCountGuard;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceImplTest {

    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private ProblemTechnologyRepository technologyRepository;
    @Mock
    private ProblemAttachmentRepository attachmentRepository;
    @Mock
    private ProblemTagRepository problemTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    private ProblemMapper problemMapper;
    @Mock
    private ProblemContentSafety contentSafety;
    @Mock
    private AttachmentValidator attachmentValidator;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private FollowNotificationService followNotificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private ViewCountGuard viewCountGuard;

    private ProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        problemMapper = new ProblemMapper();
        service = new ProblemServiceImpl(
                problemRepository,
                technologyRepository,
                attachmentRepository,
                problemTagRepository,
                tagRepository,
                categoryRepository,
                userProfileRepository,
                problemMapper,
                contentSafety,
                attachmentValidator,
                objectStorageService,
                followNotificationService,
                eventPublisher,
                new TagResolver(tagRepository),
                solutionRepository,
                viewCountGuard
        );
        // Every view counts unless a test says otherwise; the dedup window is
        // covered by ViewCountGuard's own test.
        lenient().when(viewCountGuard.shouldCount(any(UUID.class)))
                .thenReturn(true);
        lenient().when(contentSafety.normalizeText(any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(problemRepository.saveAndFlush(any(Problem.class)))
                .thenAnswer(invocation -> {
                    Problem problem = invocation.getArgument(0);
                    if (problem.getId() == null) {
                        problem.setId(UUID.randomUUID());
                    }
                    return problem;
                });
        lenient().when(tagRepository.saveAndFlush(any(Tag.class)))
                .thenAnswer(invocation -> {
                    Tag tag = invocation.getArgument(0);
                    if (tag.getId() == null) {
                        tag.setId(UUID.randomUUID());
                    }
                    return tag;
                });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsDraftWithAuthorFromJwt() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        authenticate(userId, "USER");
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(user(userId)));
        when(problemTagRepository.findAllByProblemId(any()))
                .thenReturn(List.of());
        when(categoryRepository.findByIdAndScopeAndIsActiveTrue(
                categoryId,
                CategoryScope.PROBLEM
        )).thenReturn(Optional.of(category(categoryId)));

        service.createDraft(new CreateProblemRequest(
                categoryId,
                "How should I structure this service?",
                SdlcPhase.DESIGN,
                "I need guidance on structuring this service for maintainability.",
                List.of(),
                Set.of(),
                Set.of()
        ));

        ArgumentCaptor<Problem> captor =
                ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).saveAndFlush(captor.capture());
        assertEquals(userId, captor.getValue().getAuthorId());
        assertEquals(ProblemStatus.DRAFT, captor.getValue().getStatus());
        assertEquals(0L, captor.getValue().getViewCount());
    }

    @Test
    void createsDraftWhenTagIdsAreOmitted() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        authenticate(userId, "USER");
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(user(userId)));
        when(problemTagRepository.findAllByProblemId(any()))
                .thenReturn(List.of());
        when(categoryRepository.findByIdAndScopeAndIsActiveTrue(
                categoryId,
                CategoryScope.PROBLEM
        )).thenReturn(Optional.of(category(categoryId)));

        service.createDraft(new CreateProblemRequest(
                categoryId,
                "How should I structure this service?",
                SdlcPhase.DESIGN,
                "I need guidance on structuring this service for maintainability.",
                List.of(),
                null,
                null
        ));

        verify(problemRepository).saveAndFlush(any(Problem.class));
    }

    @Test
    void submitsACompleteDraft() {
        UUID userId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Problem problem = editableProblem(userId);
        problem.setCategoryId(categoryId);
        problem.setDescription(
                "This description contains enough detail to be reviewed."
        );
        authenticate(userId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        Category category = category(categoryId);
        when(categoryRepository.findByIdAndScopeAndIsActiveTrue(
                categoryId,
                CategoryScope.PROBLEM
        )).thenReturn(Optional.of(category));
        when(technologyRepository
                .findAllByProblemIdInOrderByNameAsc(anyCollection()))
                .thenReturn(List.of(technology(problem)));
        stubResponseDependencies(problem, userId);

        service.submit(problem.getId());

        assertEquals(ProblemStatus.PENDING_APPROVAL, problem.getStatus());
        assertNull(problem.getPublishedAt());
    }

    @Test
    void rejectsIncompleteSubmission() {
        UUID userId = UUID.randomUUID();
        Problem problem = editableProblem(userId);
        problem.setDescription("Too short");
        authenticate(userId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(problem.getId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(problemRepository, never()).saveAndFlush(problem);
    }

    @Test
    void hidesAnotherAuthorsDraft() {
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Problem problem = editableProblem(authorId);
        authenticate(otherUserId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.findById(problem.getId())
        );
    }

    @Test
    void updateReplacesTagsAndAdjustsCounters() {
        UUID authorId = UUID.randomUUID();
        Problem problem = editableProblem(authorId);
        Tag oldTag = tag(UUID.randomUUID(), "java");
        Tag newTag = tag(UUID.randomUUID(), "spring");
        ProblemTag oldAssociation = ProblemTag.builder()
                .id(new ProblemTagId(problem.getId(), oldTag.getId()))
                .problem(problem)
                .tag(oldTag)
                .build();
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(tagRepository.findAllByIdIn(Set.of(newTag.getId())))
                .thenReturn(List.of(newTag));
        when(problemTagRepository.findAllByProblemId(problem.getId()))
                .thenReturn(List.of(oldAssociation));
        stubResponseDependencies(problem, authorId);

        service.update(
                problem.getId(),
                new ProblemUpdateRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        Set.of(newTag.getId()),
                        null
                ),
                problem.getVersion()
        );

        verify(problemTagRepository).deleteById(
                new ProblemTagId(problem.getId(), oldTag.getId())
        );
        verify(tagRepository).decrementUsageCounts(Set.of(oldTag.getId()));
        verify(tagRepository).incrementUsageCounts(Set.of(newTag.getId()));
        verify(problemTagRepository).saveAllAndFlush(anyList());
    }

    @Test
    void rejectsDuplicateTechnologyIgnoringCase() {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "USER");
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(user(userId)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(new CreateProblemRequest(
                        null,
                        "Duplicate technology example",
                        null,
                        null,
                        List.of(
                                new ProblemTechnologyRequest("Node.js", "18"),
                                new ProblemTechnologyRequest("node.JS", "18")
                        ),
                        Set.of(),
                        Set.of()
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsDuplicateNormalizedTagNames() {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "USER");
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(user(userId)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createDraft(new CreateProblemRequest(
                        null,
                        "Duplicate normalized tag example",
                        null,
                        null,
                        List.of(),
                        Set.of(),
                        Set.of("Spring Boot", "spring-boot")
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void nonAdminCannotModerateStatus() {
        UUID userId = UUID.randomUUID();
        authenticate(userId, "USER");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.moderate(
                        UUID.randomUUID(),
                        new ProblemModerationRequest(
                                ProblemStatus.PUBLISHED
                        )
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void adminPublishesPendingProblem() {
        UUID adminId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Problem problem = editableProblem(authorId);
        problem.setStatus(ProblemStatus.PENDING_APPROVAL);
        problem.setCategoryId(categoryId);
        problem.setDescription(
                "This description contains enough detail to be published."
        );
        authenticate(adminId, "ADMIN");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        Category category = category(categoryId);
        when(categoryRepository.findByIdAndScopeAndIsActiveTrue(
                categoryId,
                CategoryScope.PROBLEM
        )).thenReturn(Optional.of(category));
        when(technologyRepository
                .findAllByProblemIdInOrderByNameAsc(anyCollection()))
                .thenReturn(List.of(technology(problem)));
        stubResponseDependencies(problem, authorId);

        service.moderate(
                problem.getId(),
                new ProblemModerationRequest(ProblemStatus.PUBLISHED)
        );

        assertEquals(ProblemStatus.PUBLISHED, problem.getStatus());
        assertTrue(problem.getPublishedAt() != null);
    }

    @Test
    void authorEditsAPublishedProblemInPlace() {
        UUID authorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, categoryId);
        Instant publishedAt = problem.getPublishedAt();
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(categoryRepository.findByIdAndScopeAndIsActiveTrue(
                categoryId,
                CategoryScope.PROBLEM
        )).thenReturn(Optional.of(category(categoryId)));
        stubResponseDependencies(problem, authorId);

        service.update(
                problem.getId(),
                new ProblemUpdateRequest(
                        null,
                        "A corrected title that is long enough",
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                problem.getVersion()
        );

        assertEquals(
                "A corrected title that is long enough",
                problem.getTitle()
        );
        assertEquals(ProblemStatus.PUBLISHED, problem.getStatus());
        assertEquals(publishedAt, problem.getPublishedAt());
    }

    @Test
    void aPublishedProblemCannotBeEditedBelowThePublishingBar() {
        UUID authorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, categoryId);
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(
                        problem.getId(),
                        new ProblemUpdateRequest(
                                null,
                                "too short",
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        problem.getVersion()
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(problemRepository, never()).saveAndFlush(problem);
    }

    @Test
    void aProblemUnderReviewCannotBeEdited() {
        UUID authorId = UUID.randomUUID();
        Problem problem = editableProblem(authorId);
        problem.setStatus(ProblemStatus.PENDING_APPROVAL);
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.update(
                        problem.getId(),
                        new ProblemUpdateRequest(
                                null,
                                "Another title entirely",
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        problem.getVersion()
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void authorDeletesTheirOwnPublishedProblem() {
        UUID authorId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, UUID.randomUUID());
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(solutionRepository
                .countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                        problem.getId()
                ))
                .thenReturn(0L);

        service.softDelete(problem.getId());

        assertTrue(problem.getDeletedAt() != null);
        verify(problemRepository).saveAndFlush(problem);
    }

    @Test
    void authorCannotDeleteAProblemThatHasPublishedSolutions() {
        UUID authorId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, UUID.randomUUID());
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(solutionRepository
                .countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                        problem.getId()
                ))
                .thenReturn(2L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.softDelete(problem.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertNull(problem.getDeletedAt());
        verify(problemRepository, never()).saveAndFlush(problem);
    }

    @Test
    void adminCanStillDeleteAnAnsweredProblem() {
        UUID authorId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, UUID.randomUUID());
        authenticate(UUID.randomUUID(), "ADMIN");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));

        service.softDelete(problem.getId());

        assertTrue(problem.getDeletedAt() != null);
        verify(solutionRepository, never())
                .countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                        any()
                );
    }

    @Test
    void deletingAProblemAlsoDiscardsItsStoredAttachments() {
        UUID authorId = UUID.randomUUID();
        Problem problem = publishedProblem(authorId, UUID.randomUUID());
        ProblemAttachment attachment = ProblemAttachment.builder()
                .id(UUID.randomUUID())
                .problem(problem)
                .originalFileName("trace.log")
                .storageKey("problems/" + problem.getId() + "/trace.log")
                .mimeType("text/plain")
                .sizeBytes(12L)
                .uploadedBy(authorId)
                .build();
        authenticate(authorId, "USER");
        when(problemRepository.findActiveById(problem.getId()))
                .thenReturn(Optional.of(problem));
        when(solutionRepository
                .countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                        problem.getId()
                ))
                .thenReturn(0L);
        when(attachmentRepository
                .findAllByProblemIdOrderByCreatedAtAsc(problem.getId()))
                .thenReturn(List.of(attachment));

        service.softDelete(problem.getId());

        verify(attachmentRepository).deleteAll(List.of(attachment));
        // No transaction is bound in a unit test, so the object store call
        // runs inline instead of waiting on a commit that never happens.
        verify(objectStorageService).delete(attachment.getStorageKey());
    }

    /**
     * The point of the batch loader: cost is per page, not per row. If this
     * ever regresses to one query per problem the listing quietly turns into
     * a hundred round trips.
     */
    @Test
    void aListingLoadsItsAssociationsOncePerPageNotPerProblem() {
        UUID authorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Problem first = publishedProblem(authorId, categoryId);
        Problem second = publishedProblem(authorId, categoryId);
        authenticate(authorId, "USER");
        when(problemRepository.findPublished(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(first, second)));
        stubResponseDependencies(first, authorId);

        service.findPublished(
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                PageRequest.of(0, 20)
        );

        verify(userProfileRepository).findAllById(anyCollection());
        verify(categoryRepository).findAllById(anyCollection());
        verify(technologyRepository)
                .findAllByProblemIdInOrderByNameAsc(anyCollection());
        verify(problemTagRepository).findAllByProblemIdIn(anyCollection());
        verify(attachmentRepository)
                .findAllByProblemIdInOrderByCreatedAtAsc(anyCollection());
        verify(userProfileRepository, never()).findById(any());
        verify(problemTagRepository, never()).findAllByProblemId(any());
    }

    private Problem publishedProblem(UUID authorId, UUID categoryId) {
        Problem problem = Problem.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .categoryId(categoryId)
                .title("A published problem title")
                .description(
                        "This description contains enough detail to stay "
                                + "published after an edit."
                )
                .status(ProblemStatus.PUBLISHED)
                .publishedAt(Instant.now())
                .build();
        return problem;
    }

    private void stubResponseDependencies(
            Problem problem,
            UUID authorId
    ) {
        lenient().when(userProfileRepository.findAllById(anyCollection()))
                .thenReturn(List.of(user(authorId)));
    }

    private Problem editableProblem(UUID authorId) {
        return Problem.builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .title("How should this problem be solved?")
                .description("")
                .status(ProblemStatus.DRAFT)
                .build();
    }

    private ProblemTechnology technology(Problem problem) {
        return ProblemTechnology.builder()
                .id(UUID.randomUUID())
                .problem(problem)
                .name("Spring Boot")
                .version("4.1")
                .build();
    }

    private UserProfile user(UUID id) {
        UserProfile user = new UserProfile();
        user.setId(id);
        user.setFullName("Test User");
        return user;
    }

    private Category category(UUID id) {
        return org.mockito.Mockito.mock(Category.class);
    }

    private Tag tag(UUID id, String slug) {
        return Tag.builder()
                .id(id)
                .name(slug)
                .slug(slug)
                .build();
    }

    private void authenticate(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
