package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.common.storage.ObjectStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
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
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemServiceImpl implements ProblemService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final int MAX_TECHNOLOGIES = 20;
    private static final Duration DOWNLOAD_LINK_VALIDITY =
            Duration.ofMinutes(5);
    private static final Set<ProblemStatus> PUBLIC_STATUSES = EnumSet.of(
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED,
            ProblemStatus.CLOSED
    );
    private static final Set<ProblemStatus> AUTHOR_EDITABLE_STATUSES =
            EnumSet.of(ProblemStatus.DRAFT, ProblemStatus.REJECTED);
    private static final Set<String> PROBLEM_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "publishedAt",
            "title",
            "viewCount",
            "status"
    );

    private final ProblemRepository problemRepository;
    private final ProblemTechnologyRepository technologyRepository;
    private final ProblemAttachmentRepository attachmentRepository;
    private final ProblemTagRepository problemTagRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProblemMapper problemMapper;
    private final ProblemContentSafety contentSafety;
    private final AttachmentValidator attachmentValidator;
    private final ObjectStorageService objectStorageService;
    private final FollowNotificationService followNotificationService;
    private final TagResolver tagResolver;

    @Autowired(required = false)
    private ProblemResponseEnricher responseEnricher;

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findPublished(
            UUID categoryId,
            SdlcPhase sdlcPhase,
            String tag,
            String technology,
            Pageable pageable
    ) {
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        String tagSlug = tag == null ? null : normalizeSlug(tag);
        String technologyName = trimToNull(technology);
        if (technologyName != null) {
            technologyName = technologyName.toLowerCase(Locale.ROOT);
        }
        return problemRepository.findPublished(
                categoryId,
                sdlcPhase,
                tagSlug,
                technologyName,
                validatedPageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findMine(Pageable pageable) {
        UUID authorId = currentUserId();
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        return problemRepository.findAllByAuthorId(
                authorId,
                validatedPageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findPublishedByAuthor(
            UUID authorId,
            Pageable pageable
    ) {
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        return problemRepository.findAllByAuthorIdAndStatusIn(
                authorId,
                PUBLIC_STATUSES,
                validatedPageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> findForModeration(
            ProblemStatus status,
            Pageable pageable
    ) {
        requireAdmin();
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROBLEM_SORT_PROPERTIES
        );
        ProblemStatus effectiveStatus = status == null
                ? ProblemStatus.PENDING_APPROVAL
                : status;
        return problemRepository.findAllByStatus(
                effectiveStatus,
                validatedPageable
        ).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProblemResponse findById(UUID id) {
        Problem problem = findProblem(id);
        if (!canView(problem)) {
            throw notFound(id);
        }
        return toResponse(problem);
    }

    @Override
    @Transactional
    public ProblemResponse createDraft(CreateProblemRequest request) {
        return create(request, false);
    }

    @Override
    @Transactional
    public ProblemResponse createAndSubmit(CreateProblemRequest request) {
        return create(request, true);
    }

    @Override
    @Transactional
    public ProblemResponse updateDraft(
            UUID id,
            ProblemUpdateRequest request,
            long expectedVersion
    ) {
        Problem problem = findOwnedEditableProblem(id);
        if (problem.getVersion() != expectedVersion) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_FAILED,
                    "The problem changed after it was read; fetch it again before editing"
            );
        }
        return updateDraft(problem, request);
    }

    private ProblemResponse updateDraft(
            Problem problem,
            ProblemUpdateRequest request
    ) {

        if (request.categoryId() != null) {
            validateDraftCategory(request.categoryId());
            problem.setCategoryId(request.categoryId());
        }
        if (request.title() != null) {
            problem.setTitle(contentSafety.normalizeText(request.title()));
        }
        if (request.problemType() != null) {
            problem.setProblemType(request.problemType());
        }
        if (request.description() != null) {
            problem.setDescription(
                    Objects.requireNonNullElse(
                            contentSafety.normalizeText(request.description()),
                            ""
                    )
            );
        }
        if (request.sdlcPhase() != null) {
            problem.setSdlcPhase(request.sdlcPhase());
        }
        if (request.expectedBehavior() != null) {
            problem.setExpectedBehavior(normalizeOptional(request.expectedBehavior()));
        }
        if (request.actualBehavior() != null) {
            problem.setActualBehavior(normalizeOptional(request.actualBehavior()));
        }
        if (request.reproductionSteps() != null) {
            problem.setReproductionSteps(normalizeSteps(request.reproductionSteps()));
        }
        if (request.environment() != null) {
            problem.setEnvironment(request.environment().stream()
                    .map(item -> new ProblemEnvironment(
                            requireText(item.technology(), "Environment technology"),
                            trimToNull(item.version())
                    ))
                    .collect(Collectors.toCollection(ArrayList::new)));
        }
        if (request.attemptsTried() != null) {
            problem.setAttemptsTried(normalizeOptional(request.attemptsTried()));
        }
        if (request.errorMessage() != null) {
            problem.setErrorMessage(normalizeOptional(request.errorMessage()));
        }
        if (request.severity() != null) {
            problem.setSeverity(request.severity());
        }
        if (request.repositoryUrl() != null) {
            problem.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        }

        Problem saved = problemRepository.saveAndFlush(problem);
        if (request.technologies() != null) {
            replaceTechnologies(saved, request.technologies());
        }
        if (request.tagIds() != null || request.newTagNames() != null) {
            replaceTags(saved, request.tagIds(), request.newTagNames());
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProblemResponse submit(UUID id) {
        Problem problem = findOwnedEditableProblem(id);
        validateForSubmission(problem);
        problem.setStatus(ProblemStatus.PENDING_APPROVAL);
        problem.setPublishedAt(null);
        return toResponse(problemRepository.saveAndFlush(problem));
    }

    @Override
    @Transactional
    public ProblemResponse moderate(
            UUID id,
            ProblemModerationRequest request
    ) {
        requireAdmin();
        if (request.status() != ProblemStatus.PUBLISHED
                && request.status() != ProblemStatus.REJECTED) {
            throw badRequest(
                    "Moderation status must be PUBLISHED or REJECTED"
            );
        }

        Problem problem = findProblem(id);
        if (problem.getStatus() != ProblemStatus.PENDING_APPROVAL) {
            throw conflict(
                    "Only a problem pending approval can be moderated"
            );
        }

        if (request.status() == ProblemStatus.PUBLISHED) {
            validateForSubmission(problem);
            problem.setPublishedAt(Instant.now());
        } else {
            problem.setPublishedAt(null);
        }
        problem.setStatus(request.status());
        Problem saved = problemRepository.saveAndFlush(problem);
        if (saved.getStatus() == ProblemStatus.PUBLISHED) {
            followNotificationService.notifyFollowers(
                    FollowType.USER,
                    saved.getAuthorId(),
                    saved.getAuthorId(),
                    "New problem published",
                    saved.getTitle(),
                    NotificationType.PROBLEM,
                    saved.getId(),
                    "problem-published:" + saved.getId()
            );
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void softDelete(UUID id) {
        Problem problem = findProblem(id);
        UUID userId = currentUserId();
        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        boolean author = problem.getAuthorId().equals(userId);

        if (!admin && !author) {
            throw forbidden("You are not allowed to delete this problem");
        }
        if (!admin && !AUTHOR_EDITABLE_STATUSES.contains(
                problem.getStatus()
        )) {
            throw conflict(
                    "Only draft or rejected problems can be deleted by "
                            + "their author"
            );
        }

        Set<UUID> associatedTagIds = problemTagRepository
                .findAllByProblemId(problem.getId())
                .stream()
                .map(problemTag -> problemTag.getTag().getId())
                .collect(Collectors.toSet());
        if (!associatedTagIds.isEmpty()) {
            tagRepository.decrementUsageCounts(associatedTagIds);
        }
        problem.softDelete();
        problemRepository.saveAndFlush(problem);
    }

    @Override
    @Transactional
    public ProblemResponse uploadAttachment(
            UUID id,
            MultipartFile file
    ) {
        Problem problem = findOwnedEditableProblem(id);
        AttachmentValidator.ValidatedAttachment validated =
                attachmentValidator.validate(file);
        String storageKey = "problems/"
                + problem.getId()
                + "/"
                + UUID.randomUUID()
                + "."
                + validated.extension();

        objectStorageService.store(
                storageKey,
                new ByteArrayInputStream(validated.content()),
                validated.sizeBytes(),
                validated.mimeType()
        );

        try {
            ProblemAttachment attachment = ProblemAttachment.builder()
                    .problem(problem)
                    .originalFileName(validated.originalFileName())
                    .storageKey(storageKey)
                    .mimeType(validated.mimeType())
                    .sizeBytes(validated.sizeBytes())
                    .uploadedBy(currentUserId())
                    .build();
            attachmentRepository.saveAndFlush(attachment);
            return toResponse(problem);
        } catch (RuntimeException exception) {
            deleteStoredObjectQuietly(storageKey);
            throw exception;
        }
    }

    @Override
    @Transactional
    public void removeAttachment(
            UUID problemId,
            UUID attachmentId
    ) {
        Problem problem = findProblem(problemId);
        boolean admin = AuthUtils.hasRole(ADMIN_ROLE);
        if (!admin) {
            requireOwnedEditable(problem);
        }

        ProblemAttachment attachment = attachmentRepository
                .findByIdAndProblemId(attachmentId, problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem attachment not found"
                ));
        String storageKey = attachment.getStorageKey();
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteStoredObjectQuietly(storageKey);
                    }
                }
        );
    }

    @Override
    @Transactional(readOnly = true)
    public URI createAttachmentDownloadUrl(
            UUID problemId,
            UUID attachmentId
    ) {
        Problem problem = findProblem(problemId);
        if (!canView(problem)) {
            throw notFound(problemId);
        }
        ProblemAttachment attachment = attachmentRepository
                .findByIdAndProblemId(attachmentId, problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem attachment not found"
                ));
        return objectStorageService.createDownloadUrl(
                attachment.getStorageKey(),
                DOWNLOAD_LINK_VALIDITY
        );
    }

    @Override
    @Transactional
    public void incrementViewCount(UUID id) {
        if (problemRepository.incrementPublicViewCount(id) == 0) {
            throw notFound(id);
        }
    }

    private ProblemResponse create(
            CreateProblemRequest request,
            boolean submit
    ) {
        UUID authorId = currentUserId();
        findAuthor(authorId);
        validateDraftCategory(request.categoryId());

        String title = contentSafety.normalizeText(request.title());
        String description = Objects.requireNonNullElse(
                contentSafety.normalizeText(request.description()),
                ""
        );

        Problem problem = problemMapper.toEntity(request, authorId);
        problem.setTitle(title);
        problem.setDescription(description);
        problem.setExpectedBehavior(normalizeOptional(request.expectedBehavior()));
        problem.setActualBehavior(normalizeOptional(request.actualBehavior()));
        problem.setReproductionSteps(normalizeSteps(request.reproductionSteps()));
        problem.setEnvironment(request.environment() == null
                ? new ArrayList<>()
                : request.environment().stream()
                .map(item -> new ProblemEnvironment(
                        requireText(item.technology(), "Environment technology"),
                        trimToNull(item.version())
                ))
                .collect(Collectors.toCollection(ArrayList::new)));
        problem.setAttemptsTried(normalizeOptional(request.attemptsTried()));
        problem.setErrorMessage(normalizeOptional(request.errorMessage()));
        problem.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        problem.setStatus(ProblemStatus.DRAFT);
        problem.setViewCount(0L);
        problem = problemRepository.saveAndFlush(problem);
        replaceTechnologies(problem, request.technologies());
        replaceTags(problem, request.tagIds(), request.newTagNames());

        if (submit) {
            validateForSubmission(problem);
            problem.setStatus(ProblemStatus.PENDING_APPROVAL);
            problem = problemRepository.saveAndFlush(problem);
        }
        return toResponse(problem);
    }

    private void validateForSubmission(Problem problem) {
        if (problem.getTitle() == null
                || problem.getTitle().length() < 10
                || problem.getTitle().length() > 180) {
            throw badRequest(
                    "A submitted problem needs a title between 10 and "
                            + "180 characters"
            );
        }
        String description = trimToNull(problem.getDescription());
        if (description == null || description.length() < 30) {
            throw badRequest(
                    "A submitted problem needs a description of at least "
                            + "30 characters"
            );
        }
        if (description.length() > 20_000) {
            throw badRequest(
                    "Problem description cannot exceed 20,000 characters"
            );
        }
        if (problem.getCategoryId() == null) {
            throw badRequest("A submitted problem needs a category");
        }
        categoryRepository.findByIdAndScopeAndIsActiveTrue(
                problem.getCategoryId(),
                CategoryScope.PROBLEM
        ).orElseThrow(() -> badRequest(
                "A submitted problem needs an active PROBLEM category"
        ));
        if (problem.getProblemType() == null) {
            throw badRequest("A submitted problem needs a problem type");
        }
        if (problem.getProblemType()
                == kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemType.BUG) {
            if (trimToNull(problem.getExpectedBehavior()) == null
                    || trimToNull(problem.getActualBehavior()) == null
                    || problem.getReproductionSteps().isEmpty()) {
                throw badRequest(
                        "BUG problems require expectedBehavior, actualBehavior, "
                                + "and at least one reproduction step"
                );
            }
        }
    }

    private void validateDraftCategory(UUID categoryId) {
        if (categoryId == null) {
            throw badRequest("Category is required");
        }
        Category category = categoryRepository
                .findByIdAndScopeAndIsActiveTrue(
                        categoryId,
                        CategoryScope.PROBLEM
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active problem category not found"
                ));
    }

    private void replaceTechnologies(
            Problem problem,
            List<ProblemTechnologyRequest> requests
    ) {
        List<ProblemTechnologyRequest> safeRequests = requests == null
                ? List.of()
                : requests;
        if (safeRequests.size() > MAX_TECHNOLOGIES) {
            throw badRequest(
                    "A problem can contain at most 20 technologies"
            );
        }

        Set<String> uniqueness = new HashSet<>();
        List<ProblemTechnology> technologies = new ArrayList<>();
        for (ProblemTechnologyRequest request : safeRequests) {
            if (request == null) {
                throw badRequest("Technology entries cannot be null");
            }
            String name = trimToNull(request.name());
            String version = trimToNull(request.version());
            if (name == null) {
                throw badRequest("Technology name cannot be blank");
            }
            String key = name.toLowerCase(Locale.ROOT)
                    + "\u0000"
                    + Objects.requireNonNullElse(version, "")
                    .toLowerCase(Locale.ROOT);
            if (!uniqueness.add(key)) {
                throw badRequest(
                        "Duplicate technology name and version"
                );
            }
            technologies.add(ProblemTechnology.builder()
                    .problem(problem)
                    .name(name)
                    .version(version)
                    .build());
        }

        technologyRepository.deleteAllByProblemId(problem.getId());
        technologyRepository.flush();
        if (!technologies.isEmpty()) {
            technologyRepository.saveAllAndFlush(technologies);
        }
    }

    private void replaceTags(
            Problem problem,
            Set<UUID> tagIds,
            Set<String> tagNames
    ) {
        Map<UUID, Tag> desired = tagResolver.resolve(
                tagIds,
                tagNames,
                "problem"
        );

        List<ProblemTag> existing = problemTagRepository
                .findAllByProblemId(problem.getId());
        Map<UUID, ProblemTag> existingByTagId = existing.stream()
                .collect(Collectors.toMap(
                        item -> item.getTag().getId(),
                        Function.identity()
                ));

        Set<UUID> removedIds = new HashSet<>(existingByTagId.keySet());
        removedIds.removeAll(desired.keySet());
        Set<UUID> addedIds = new HashSet<>(desired.keySet());
        addedIds.removeAll(existingByTagId.keySet());

        removedIds.forEach(tagId -> problemTagRepository.deleteById(
                new ProblemTagId(problem.getId(), tagId)
        ));
        if (!removedIds.isEmpty()) {
            problemTagRepository.flush();
            tagRepository.decrementUsageCounts(removedIds);
        }

        if (!addedIds.isEmpty()) {
            List<ProblemTag> additions = addedIds.stream()
                    .map(tagId -> ProblemTag.builder()
                            .id(new ProblemTagId(problem.getId(), tagId))
                            .problem(problem)
                            .tag(desired.get(tagId))
                            .build())
                    .toList();
            problemTagRepository.saveAllAndFlush(additions);
            tagRepository.incrementUsageCounts(addedIds);
        }
    }

    private Problem findOwnedEditableProblem(UUID id) {
        Problem problem = findProblem(id);
        requireOwnedEditable(problem);
        return problem;
    }

    private void requireOwnedEditable(Problem problem) {
        if (!problem.getAuthorId().equals(currentUserId())) {
            throw forbidden("You are not the author of this problem");
        }
        if (!AUTHOR_EDITABLE_STATUSES.contains(problem.getStatus())) {
            throw conflict(
                    "Only draft or rejected problems can be edited"
            );
        }
    }

    private boolean canView(Problem problem) {
        if (PUBLIC_STATUSES.contains(problem.getStatus())
                || AuthUtils.hasRole(ADMIN_ROLE)) {
            return true;
        }
        return currentUserIdIfPresent()
                .map(problem.getAuthorId()::equals)
                .orElse(false);
    }

    private ProblemResponse toResponse(Problem problem) {
        UserProfile author = findAuthor(problem.getAuthorId());
        Category category = problem.getCategoryId() == null
                ? null
                : categoryRepository.findById(problem.getCategoryId())
                .orElse(null);
        ProblemResponseMetrics metrics = responseEnricher == null
                ? ProblemResponseMetrics.empty()
                : responseEnricher.read(problem);
        return problemMapper.toResponse(
                problem,
                author,
                category,
                technologyRepository.findAllByProblemIdOrderByNameAsc(
                        problem.getId()
                ),
                problemTagRepository.findAllByProblemId(problem.getId()),
                attachmentRepository
                        .findAllByProblemIdOrderByCreatedAtAsc(problem.getId()),
                contentSafety.warnings(
                        problem.getTitle(),
                        problem.getDescription()
                ),
                metrics
        );
    }

    private Problem findProblem(UUID id) {
        return problemRepository.findActiveById(id)
                .orElseThrow(() -> notFound(id));
    }

    private UserProfile findAuthor(UUID id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem author profile not found"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "The Keycloak subject must be a UUID"
            );
        }
    }

    private Optional<UUID> currentUserIdIfPresent() {
        Authentication authentication = AuthUtils.getAuth();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication instanceof JwtAuthenticationToken jwt)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    jwt.getToken().getSubject()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw forbidden("Only ADMIN can moderate problems");
        }
    }

    private String normalizeSlug(String value) {
        return TagResolver.normalizeSlug(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptional(String value) {
        String normalized = contentSafety.normalizeText(value);
        return trimToNull(normalized);
    }

    private List<String> normalizeSteps(List<String> steps) {
        if (steps == null) {
            return new ArrayList<>();
        }
        if (steps.size() > 20) {
            throw badRequest("A problem can contain at most 20 reproduction steps");
        }
        return steps.stream()
                .map(step -> requireText(step, "Reproduction step"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String requireText(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw badRequest(fieldName + " cannot be blank");
        }
        return normalized;
    }

    private void deleteStoredObjectQuietly(String storageKey) {
        try {
            objectStorageService.delete(storageKey);
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to delete object storage key {}",
                    storageKey,
                    exception
            );
        }
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException(
                "Problem not found with id: " + id
        );
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
