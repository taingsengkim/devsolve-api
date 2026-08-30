package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionWorkflow;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShowCaseStepServiceImpl implements ShowCaseStepService {

    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowCasesRepository showCasesRepository;
    private final ShowcaseStepMapper showcaseStepMapper;
    private final ShowcaseRevisionRepository showcaseRevisionRepository;
    private final ShowcaseStepRevisionRepository
            showcaseStepRevisionRepository;
    private final ShowcaseRevisionWorkflow showcaseRevisionWorkflow;
    private final ImageStorageService imageStorageService;

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public ShowcaseStepResponse create(UUID showcaseId, CreateShowcaseStepRequest request) {
        UUID currentUserId = extractCurrentUserId();
        ShowCases showcase = findActiveShowcase(showcaseId);

        requireOwner(showcase, currentUserId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            return createRevisionStep(
                    showcase,
                    currentUserId,
                    request
            );
        }

        ensureStepNumberAvailable(
                showcaseId,
                request.stepNumber(),
                null
        );

        ShowcaseStep step = showcaseStepMapper
                .mapCreateShowcaseStepRequestToShowcaseStep(request);

        step.setShowcase(showcase);

        ShowcaseStep saved = showcaseStepRepository.save(step);
        resubmitInitialShowcaseIfRejected(showcase);

        return showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(saved);
    }

    @Override
    public List<ShowcaseStepResponse> getAll(UUID showcaseId) {
        ShowCases showcase = findActiveShowcase(showcaseId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED
                && isCurrentOwner(showcase)) {
            ShowcaseRevision revision = showcaseRevisionRepository
                    .findByShowcase_Id(showcaseId)
                    .orElse(null);

            if (revision != null) {
                return showcaseRevisionWorkflow
                        .getCandidateSteps(revision.getId())
                        .stream()
                        .map(showcaseStepMapper
                                ::mapShowcaseStepRevisionToShowcaseStepResponse)
                        .toList();
            }
        }

        return showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcase.getId())
                .stream()
                .map(showcaseStepMapper::mapShowcaseStepToShowcaseStepResponse)
                .toList();
    }

    @Override
    public ShowcaseStepResponse getById(UUID showcaseId, UUID stepId) {
        ShowCases showcase = findActiveShowcase(showcaseId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED
                && isCurrentOwner(showcase)
                && showcaseRevisionRepository
                        .findByShowcase_Id(showcaseId)
                        .isPresent()) {
            return showcaseStepMapper
                    .mapShowcaseStepRevisionToShowcaseStepResponse(
                            findRevisionStep(showcaseId, stepId)
                    );
        }

        ShowcaseStep step = findPublishedStep(showcaseId, stepId);

        return showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(step);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public ShowcaseStepResponse update(
            UUID showcaseId,
            UUID stepId,
            UpdateShowcaseStepRequest request
    ) {
        UUID currentUserId = extractCurrentUserId();
        ShowCases showcase = findActiveShowcase(showcaseId);

        requireOwner(showcase, currentUserId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            return updateRevisionStep(
                    showcase,
                    currentUserId,
                    stepId,
                    request
            );
        }

        ShowcaseStep step = findPublishedStep(showcaseId, stepId);

        if (request.stepNumber() != null) {
            ensureStepNumberAvailable(
                    showcaseId,
                    request.stepNumber(),
                    stepId
            );
            step.setStepNumber(request.stepNumber());
        }

        if (request.title() != null) {
            step.setTitle(request.title());
        }

        if (request.description() != null) {
            step.setDescription(request.description());
        }

        if (request.codeSnippet() != null) {
            step.setCodeSnippet(request.codeSnippet());
        }

        if (request.imageUrl() != null) {
            step.setImageUrl(request.imageUrl());
        }

        if (request.diagramUrl() != null) {
            step.setDiagramUrl(request.diagramUrl());
        }

        ShowcaseStep updated = showcaseStepRepository.save(step);
        resubmitInitialShowcaseIfRejected(showcase);

        return showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(updated);
    }

    /**
     * Stores {@code file} in the step's {@code kind} slot.
     *
     * <p>Mirrors {@link #update}: on a published showcase the upload lands in
     * the revision so a moderator sees it before readers do, which means the
     * live step keeps its current image until the revision is approved.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public ShowcaseStepResponse uploadImage(
            UUID showcaseId,
            UUID stepId,
            ShowcaseStepImageKind kind,
            MultipartFile file
    ) {
        UUID currentUserId = extractCurrentUserId();
        ShowCases showcase = findActiveShowcase(showcaseId);

        requireOwner(showcase, currentUserId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showcase, currentUserId);
            ShowcaseStepRevision step =
                    findRevisionStep(showcaseId, stepId);

            kind.setUrl(step, imageStorageService.replace(
                    imagePrefix(showcaseId, stepId, kind),
                    ImageStorageService.supersededUrl(
                            kind.urlOf(step),
                            publishedUrlOf(step, kind)
                    ),
                    file
            ));

            ShowcaseStepRevision updated =
                    showcaseStepRevisionRepository.save(step);
            showcaseRevisionWorkflow.submit(revision, currentUserId);

            return showcaseStepMapper
                    .mapShowcaseStepRevisionToShowcaseStepResponse(updated);
        }

        ShowcaseStep step = findPublishedStep(showcaseId, stepId);

        kind.setUrl(step, imageStorageService.replace(
                imagePrefix(showcaseId, stepId, kind),
                kind.urlOf(step),
                file
        ));

        ShowcaseStep updated = showcaseStepRepository.save(step);
        resubmitInitialShowcaseIfRejected(showcase);

        return showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(updated);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public ShowcaseStepResponse removeImage(
            UUID showcaseId,
            UUID stepId,
            ShowcaseStepImageKind kind
    ) {
        UUID currentUserId = extractCurrentUserId();
        ShowCases showcase = findActiveShowcase(showcaseId);

        requireOwner(showcase, currentUserId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showcase, currentUserId);
            ShowcaseStepRevision step =
                    findRevisionStep(showcaseId, stepId);

            imageStorageService.remove(
                    ImageStorageService.supersededUrl(
                            kind.urlOf(step),
                            publishedUrlOf(step, kind)
                    )
            );
            kind.setUrl(step, null);

            ShowcaseStepRevision updated =
                    showcaseStepRevisionRepository.save(step);
            showcaseRevisionWorkflow.submit(revision, currentUserId);

            return showcaseStepMapper
                    .mapShowcaseStepRevisionToShowcaseStepResponse(updated);
        }

        ShowcaseStep step = findPublishedStep(showcaseId, stepId);

        imageStorageService.remove(kind.urlOf(step));
        kind.setUrl(step, null);

        ShowcaseStep updated = showcaseStepRepository.save(step);
        resubmitInitialShowcaseIfRejected(showcase);

        return showcaseStepMapper
                .mapShowcaseStepToShowcaseStepResponse(updated);
    }

    private String imagePrefix(
            UUID showcaseId,
            UUID stepId,
            ShowcaseStepImageKind kind
    ) {
        return "showcases/" + showcaseId
                + "/steps/" + stepId
                + "/" + kind.storageFolder();
    }

    /**
     * The image the live step still points at, or {@code null} when this
     * revision step has no published counterpart. A revision snapshot starts
     * out sharing the published image, which has to outlive the revision's
     * own edits.
     */
    private String publishedUrlOf(
            ShowcaseStepRevision step,
            ShowcaseStepImageKind kind
    ) {
        if (step.getSourceStepId() == null) {
            return null;
        }
        return showcaseStepRepository
                .findById(step.getSourceStepId())
                .map(kind::urlOf)
                .orElse(null);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public void delete(UUID showcaseId, UUID stepId) {
        UUID currentUserId = extractCurrentUserId();
        ShowCases showcase = findActiveShowcase(showcaseId);

        requireOwner(showcase, currentUserId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showcase, currentUserId);
            ShowcaseStepRevision step =
                    findRevisionStep(showcaseId, stepId);

            showcaseStepRevisionRepository.delete(step);
            showcaseRevisionWorkflow.submit(
                    revision,
                    currentUserId
            );
            return;
        }

        ShowcaseStep step = findPublishedStep(showcaseId, stepId);
        showcaseStepRepository.delete(step);
        resubmitInitialShowcaseIfRejected(showcase);
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private ShowCases findActiveShowcase(UUID showcaseId) {
        return showCasesRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                ));
    }

    private ShowcaseStep findPublishedStep(
            UUID showcaseId,
            UUID stepId
    ) {
        return showcaseStepRepository
                .findByIdAndShowcase_IdAndShowcase_DeletedAtIsNull(
                        stepId,
                        showcaseId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Step not found."
                ));
    }

    private ShowcaseStepRevision findRevisionStep(
            UUID showcaseId,
            UUID stepId
    ) {
        return showcaseStepRevisionRepository
                .findByIdAndRevision_Showcase_Id(
                        stepId,
                        showcaseId
                )
                .or(() -> showcaseStepRevisionRepository
                        .findBySourceStepIdAndRevision_Showcase_Id(
                                stepId,
                                showcaseId
                        ))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Step not found in the unpublished revision."
                ));
    }

    private void requireOwner(
            ShowCases showcase,
            UUID currentUserId
    ) {
        if (!showcase.getAuthor().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only modify steps in your own showcase."
            );
        }
    }

    private void ensureStepNumberAvailable(
            UUID showcaseId,
            Integer stepNumber,
            UUID excludedStepId
    ) {
        boolean exists = excludedStepId == null
                ? showcaseStepRepository
                        .existsByShowcase_IdAndStepNumber(
                                showcaseId,
                                stepNumber
                        )
                : showcaseStepRepository
                        .existsByShowcase_IdAndStepNumberAndIdNot(
                                showcaseId,
                                stepNumber,
                                excludedStepId
                        );

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Step number already exists in this showcase."
            );
        }
    }

    private ShowcaseStepResponse createRevisionStep(
            ShowCases showcase,
            UUID currentUserId,
            CreateShowcaseStepRequest request
    ) {
        ShowcaseRevision revision = showcaseRevisionWorkflow
                .getOrCreate(showcase, currentUserId);

        ensureRevisionStepNumberAvailable(
                revision.getId(),
                request.stepNumber(),
                null
        );

        ShowcaseStepRevision step = showcaseStepMapper
                .mapCreateShowcaseStepRequestToShowcaseStepRevision(
                        request
                );
        step.setRevision(revision);

        ShowcaseStepRevision saved =
                showcaseStepRevisionRepository.save(step);
        showcaseRevisionWorkflow.submit(revision, currentUserId);

        return showcaseStepMapper
                .mapShowcaseStepRevisionToShowcaseStepResponse(saved);
    }

    private ShowcaseStepResponse updateRevisionStep(
            ShowCases showcase,
            UUID currentUserId,
            UUID stepId,
            UpdateShowcaseStepRequest request
    ) {
        ShowcaseRevision revision = showcaseRevisionWorkflow
                .getOrCreate(showcase, currentUserId);
        ShowcaseStepRevision step =
                findRevisionStep(showcase.getId(), stepId);

        if (request.stepNumber() != null) {
            ensureRevisionStepNumberAvailable(
                    revision.getId(),
                    request.stepNumber(),
                    step.getId()
            );
            step.setStepNumber(request.stepNumber());
        }

        applyUpdate(step, request);

        ShowcaseStepRevision updated =
                showcaseStepRevisionRepository.save(step);
        showcaseRevisionWorkflow.submit(revision, currentUserId);

        return showcaseStepMapper
                .mapShowcaseStepRevisionToShowcaseStepResponse(updated);
    }

    private void applyUpdate(
            ShowcaseStepRevision step,
            UpdateShowcaseStepRequest request
    ) {
        if (request.title() != null) {
            step.setTitle(request.title());
        }
        if (request.description() != null) {
            step.setDescription(request.description());
        }
        if (request.codeSnippet() != null) {
            step.setCodeSnippet(request.codeSnippet());
        }
        if (request.imageUrl() != null) {
            step.setImageUrl(request.imageUrl());
        }
        if (request.diagramUrl() != null) {
            step.setDiagramUrl(request.diagramUrl());
        }
    }

    private void ensureRevisionStepNumberAvailable(
            UUID revisionId,
            Integer stepNumber,
            UUID excludedStepId
    ) {
        boolean exists = excludedStepId == null
                ? showcaseStepRevisionRepository
                        .existsByRevision_IdAndStepNumber(
                                revisionId,
                                stepNumber
                        )
                : showcaseStepRevisionRepository
                        .existsByRevision_IdAndStepNumberAndIdNot(
                                revisionId,
                                stepNumber,
                                excludedStepId
                        );

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Step number already exists in this revision."
            );
        }
    }

    private void resubmitInitialShowcaseIfRejected(
            ShowCases showcase
    ) {
        if (showcase.getReviewStatus() != ReviewStatus.REJECTED) {
            return;
        }

        showcase.setReviewStatus(ReviewStatus.PENDING);
        showcase.setReviewedBy(null);
        showcase.setReviewedAt(null);
        showcase.setRejectionReason(null);
        showCasesRepository.save(showcase);
    }

    private boolean isCurrentOwner(ShowCases showcase) {
        return showcase.getAuthor()
                .getId()
                .equals(extractCurrentUserId());
    }
}
