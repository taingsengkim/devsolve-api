package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewQueueItemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepMapper;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowCasesServiceImpl implements ShowCasesService {
    private final ShowCasesRepository showCaseRepository;
    private final UserProfileRepository userProfileRepository;
    private final ShowCasesMapper showCasesMapper;
    private final CategoryRepository categoryRepository;
    private final ShowcaseRevisionRepository showcaseRevisionRepository;
    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowcaseStepMapper showcaseStepMapper;
    private final ShowcaseRevisionWorkflow showcaseRevisionWorkflow;
    private final ShowcaseReviewHistoryRepository
            showcaseReviewHistoryRepository;

    @Override
    public Page<ShowCasesResponse> getAllPublished(
            String query,
            UUID categoryId,
            String sortBy,
            String sortDirection,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        parseSortDirection(sortDirection),
                        validatePublishedSortProperty(sortBy)
                )
        );

        return showCaseRepository
                .searchPublished(
                        ReviewStatus.APPROVED,
                        normalizeQuery(query),
                        categoryId,
                        pageable
                )
                .map(showCasesMapper::mapShowCaseToShowCaseResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowCasesSummaryResponse> getMyShowcases(
            int pageNumber,
            int pageSize
    ) {
        UUID authorId = extractCurrentUserId();

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<ShowCases> showcases = showCaseRepository
                .findByAuthor_IdAndDeletedAtIsNull(
                        authorId,
                        pageable
                );

        if (showcases.isEmpty()) {
            return showcases.map(
                    showCasesMapper::mapShowCaseToSummaryResponse
            );
        }

        Map<UUID, ShowcaseRevision> revisionsByShowcaseId =
                showcaseRevisionRepository
                        .findByShowcase_IdIn(
                                showcases.stream()
                                        .map(ShowCases::getId)
                                        .toList()
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                revision -> revision
                                        .getShowcase()
                                        .getId(),
                                Function.identity()
                        ));

        return showcases.map(showcase -> {
            ShowcaseRevision revision =
                    revisionsByShowcaseId.get(showcase.getId());

            return revision == null
                    ? showCasesMapper
                            .mapShowCaseToSummaryResponse(showcase)
                    : showCasesMapper
                            .mapRevisionToSummaryResponse(revision);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowcaseReviewQueueItemResponse> getReviewQueue(
            ReviewStatus reviewStatus,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return showCaseRepository
                .findReviewQueue(reviewStatus.name(), pageable)
                .map(this::mapReviewQueueItem);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowcaseReviewDetailResponse getReviewDetail(
            UUID showcaseId
    ) {
        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                ));

        ShowcaseRevision revision = showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .orElse(null);

        if (revision == null) {
            List<ShowcaseStepResponse> steps = showcaseStepRepository
                    .findByShowcase_IdOrderByStepNumberAsc(showcaseId)
                    .stream()
                    .map(showcaseStepMapper
                            ::mapShowcaseStepToShowcaseStepResponse)
                    .toList();

            return showCasesMapper.mapShowCaseToReviewDetail(
                    showcase,
                    steps
            );
        }

        List<ShowcaseStepResponse> steps =
                showcaseRevisionWorkflow
                        .getCandidateSteps(revision.getId())
                        .stream()
                        .map(showcaseStepMapper
                                ::mapShowcaseStepRevisionToShowcaseStepResponse)
                        .toList();

        return showCasesMapper.mapRevisionToReviewDetail(
                revision,
                steps
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ShowcaseReviewDetailResponse getMyRevision(
            UUID showcaseId
    ) {
        UUID authorId = extractCurrentUserId();
        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                ));

        if (!showcase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only view a revision of your own showcase."
            );
        }

        ShowcaseRevision revision = showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "This showcase has no unpublished revision."
                ));

        List<ShowcaseStepResponse> steps =
                showcaseRevisionWorkflow
                        .getCandidateSteps(revision.getId())
                        .stream()
                        .map(showcaseStepMapper
                                ::mapShowcaseStepRevisionToShowcaseStepResponse)
                        .toList();

        return showCasesMapper.mapRevisionToReviewDetail(
                revision,
                steps
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowcaseReviewHistoryResponse> getReviewHistory(
            UUID showcaseId,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize
        );

        return showcaseReviewHistoryRepository
                .findByShowcaseIdOrderByReviewedAtDesc(
                        showcaseId,
                        pageable
                )
                .map(showCasesMapper::mapReviewHistory);
    }

    @Override
    public ShowCasesResponse getById(UUID id) {
        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        if (showcase.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Showcase not found."
            );
        }

        List<ShowcaseStepResponse> steps = showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(id)
                .stream()
                .map(showcaseStepMapper::mapShowcaseStepToShowcaseStepResponse)
                .toList();

        return showCasesMapper.mapShowCaseToDetailResponse(
                showcase,
                steps
        );
    }

    @Override
    public ShowCasesResponse create(CreateShowCasesRequest request) {
        UUID authorId = UUID.fromString(
                AuthUtils.extractUserId()
        );

        UserProfile author = userProfileRepository
                .findById(authorId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found."
                        )
                );

        Category category = categoryRepository
                .findById(request.categoryId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Category not found."
                        )
                );

        if (showCaseRepository
                .existsByAuthor_IdAndTitleAndDeletedAtIsNull(
                        authorId,
                        request.title()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a showcase with this title."
            );
        }

        if (request.repoUrl() != null
                && showCaseRepository.existsByRepoUrl(
                request.repoUrl()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Repository URL already exists."
            );
        }

        if (request.liveUrl() != null
                && showCaseRepository.existsByLiveUrl(
                request.liveUrl()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Live URL already exists."
            );
        }

        ShowCases showCase =
                showCasesMapper
                        .mapCreateShowCaseRequestToShowCase(request);

        showCase.setAuthor(author);
        showCase.setCategory(category);
        showCase.setReviewStatus(ReviewStatus.PENDING);
        showCase.setViewCount(0);

        ShowCases saved =
                showCaseRepository.save(showCase);

        return showCasesMapper
                .mapShowCaseToShowCaseResponse(saved);
    }

    @Override
    @Transactional
    public ShowCasesResponse update(
            UUID showcaseId,
            UpdateShowCasesRequest request
    ) {
        UUID authorId = extractCurrentUserId();

        ShowCases showCase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own showcase."
            );
        }

        validateUniqueTitle(
                authorId,
                showcaseId,
                request.title()
        );

        if (showCase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showCase, authorId);

            applyUpdate(revision, request);
            showcaseRevisionWorkflow.submit(revision, authorId);

            return showCasesMapper
                    .mapRevisionToShowCaseResponse(revision);
        }

        applyUpdate(showCase, request);

        showCase.setReviewStatus(ReviewStatus.PENDING);
        showCase.setReviewedBy(null);
        showCase.setReviewedAt(null);
        showCase.setRejectionReason(null);

        ShowCases saved =
                showCaseRepository.save(showCase);

        return showCasesMapper
                .mapShowCaseToShowCaseResponse(saved);
    }

    @Override
    public void softDelete(UUID showcaseId) {
        UUID authorId = UUID.fromString(
                AuthUtils.extractUserId()
        );

        ShowCases showCase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own showcase."
            );
        }

        showCase.setDeletedAt(LocalDateTime.now());

        showCaseRepository.save(showCase);
    }

    @Override
    @Transactional
    public void hardDelete(UUID showcaseId) {
        UUID authorId = extractCurrentUserId();

        ShowCases showCase = showCaseRepository
                .findById(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own showcase."
            );
        }

        showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .ifPresent(showcaseRevisionWorkflow::discard);
        showCaseRepository.delete(showCase);
    }

    @Override
    @Transactional
    public void cancelRevision(UUID showcaseId) {
        UUID authorId = extractCurrentUserId();

        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                ));

        if (!showcase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only cancel a revision of your own showcase."
            );
        }

        ShowcaseRevision revision = showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "This showcase has no unpublished revision."
                ));

        showcaseRevisionWorkflow.discard(revision);
    }

    @Override
    @Transactional
    public void restore(UUID showcaseId) {
        UUID authorId = extractCurrentUserId();
        ShowCases showcase = showCaseRepository
                .findById(showcaseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                ));

        if (!showcase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only restore your own showcase."
            );
        }

        if (showcase.getDeletedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Showcase is not deleted."
            );
        }

        showcase.setDeletedAt(null);
        showCaseRepository.save(showcase);
    }

    @Override
    @Transactional
    public ShowcaseViewCountResponse incrementViewCount(
            UUID showcaseId
    ) {
        int updated = showCaseRepository.incrementViewCount(
                showcaseId,
                ReviewStatus.APPROVED
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Showcase not found."
            );
        }

        Integer viewCount = showCaseRepository
                .findViewCountById(showcaseId);

        return new ShowcaseViewCountResponse(
                showcaseId,
                viewCount != null ? viewCount : 0
        );
    }

    @Override
    @Transactional
    public ShowCasesResponse updateStatus(
            UUID showcaseId,
            UpdateShowcaseStatusRequest request
    ) {
        validateReviewRequest(request);

        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        UUID reviewerId = extractCurrentUserId();
        ShowcaseRevision revision = showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .orElse(null);

        if (revision != null) {
            return reviewRevision(
                    showcase,
                    revision,
                    request,
                    reviewerId
            );
        }

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This showcase has no pending revision to review."
            );
        }

        LocalDateTime submittedAt = showcase.getUpdatedAt();
        LocalDateTime reviewedAt = LocalDateTime.now();

        showcase.setReviewStatus(request.reviewStatus());
        showcase.setReviewedBy(reviewerId);
        showcase.setReviewedAt(reviewedAt);
        showcase.setRejectionReason(
                request.reviewStatus() == ReviewStatus.REJECTED
                        ? request.rejectionReason().trim()
                        : null
        );

        ShowCases saved = showCaseRepository.save(showcase);
        showcaseReviewHistoryRepository.save(
                createInitialReviewHistory(
                        saved,
                        submittedAt
                )
        );

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved);
    }

    private ShowCasesResponse reviewRevision(
            ShowCases showcase,
            ShowcaseRevision revision,
            UpdateShowcaseStatusRequest request,
            UUID reviewerId
    ) {
        if (revision.getReviewStatus() != ReviewStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This showcase revision has already been reviewed."
            );
        }

        if (request.reviewStatus() == ReviewStatus.REJECTED) {
            LocalDateTime submittedAt = revision.getUpdatedAt();
            revision.setReviewStatus(ReviewStatus.REJECTED);
            revision.setReviewedBy(reviewerId);
            revision.setReviewedAt(LocalDateTime.now());
            revision.setRejectionReason(
                    request.rejectionReason().trim()
            );

            ShowcaseRevision rejected =
                    showcaseRevisionRepository.save(revision);
            showcaseReviewHistoryRepository.save(
                    createRevisionReviewHistory(
                            rejected,
                            submittedAt
                    )
            );

            return showCasesMapper
                    .mapRevisionToShowCaseResponse(rejected);
        }

        LocalDateTime submittedAt = revision.getUpdatedAt();
        LocalDateTime reviewedAt = LocalDateTime.now();
        revision.setReviewStatus(ReviewStatus.APPROVED);
        revision.setReviewedBy(reviewerId);
        revision.setReviewedAt(reviewedAt);
        revision.setRejectionReason(null);

        showcaseRevisionWorkflow.promoteSteps(showcase, revision);
        applyRevision(showcase, revision);
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        showcase.setReviewedBy(reviewerId);
        showcase.setReviewedAt(reviewedAt);
        showcase.setRejectionReason(null);

        ShowCases saved = showCaseRepository.save(showcase);
        showcaseReviewHistoryRepository.save(
                createRevisionReviewHistory(
                        revision,
                        submittedAt
                )
        );
        showcaseRevisionWorkflow.discard(revision);

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved);
    }

    private void applyUpdate(
            ShowcaseRevision revision,
            UpdateShowCasesRequest request
    ) {
        if (request.categoryId() != null) {
            revision.setCategory(resolveCategory(request.categoryId()));
        }
        if (request.title() != null) {
            revision.setTitle(request.title());
        }
        if (request.overview() != null) {
            revision.setOverview(request.overview());
        }
        if (request.coverImageUrl() != null) {
            revision.setCoverImageUrl(request.coverImageUrl());
        }
        if (request.liveUrl() != null) {
            revision.setLiveUrl(request.liveUrl());
        }
        if (request.repoUrl() != null) {
            revision.setRepoUrl(request.repoUrl());
        }
        if (request.videoUrl() != null) {
            revision.setVideoUrl(request.videoUrl());
        }
    }

    private void applyUpdate(
            ShowCases showcase,
            UpdateShowCasesRequest request
    ) {
        if (request.categoryId() != null) {
            showcase.setCategory(resolveCategory(request.categoryId()));
        }
        if (request.title() != null) {
            showcase.setTitle(request.title());
        }
        if (request.overview() != null) {
            showcase.setOverview(request.overview());
        }
        if (request.coverImageUrl() != null) {
            showcase.setCoverImageUrl(request.coverImageUrl());
        }
        if (request.liveUrl() != null) {
            showcase.setLiveUrl(request.liveUrl());
        }
        if (request.repoUrl() != null) {
            showcase.setRepoUrl(request.repoUrl());
        }
        if (request.videoUrl() != null) {
            showcase.setVideoUrl(request.videoUrl());
        }
    }

    private Category resolveCategory(UUID categoryId) {
        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found."
                ));
    }

    private void validateUniqueTitle(
            UUID authorId,
            UUID showcaseId,
            String title
    ) {
        if (title != null
                && showCaseRepository
                .existsByAuthor_IdAndTitleAndIdNotAndDeletedAtIsNull(
                        authorId,
                        title,
                        showcaseId
                )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have another showcase with this title."
            );
        }
    }

    private void validateReviewRequest(
            UpdateShowcaseStatusRequest request
    ) {
        if (request.reviewStatus() == ReviewStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review decision must be APPROVED or REJECTED."
            );
        }

        boolean hasReason = request.rejectionReason() != null
                && !request.rejectionReason().isBlank();

        if (request.reviewStatus() == ReviewStatus.REJECTED
                && !hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required when rejecting a showcase."
            );
        }

        if (request.reviewStatus() != ReviewStatus.REJECTED
                && hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is only allowed for rejected showcases."
            );
        }
    }

    private void applyRevision(
            ShowCases showcase,
            ShowcaseRevision revision
    ) {
        showcase.setCategory(revision.getCategory());
        showcase.setTitle(revision.getTitle());
        showcase.setOverview(revision.getOverview());
        showcase.setCoverImageUrl(revision.getCoverImageUrl());
        showcase.setLiveUrl(revision.getLiveUrl());
        showcase.setRepoUrl(revision.getRepoUrl());
        showcase.setVideoUrl(revision.getVideoUrl());
    }

    private ShowcaseReviewHistory createInitialReviewHistory(
            ShowCases showcase,
            LocalDateTime submittedAt
    ) {
        ShowcaseReviewHistory history =
                new ShowcaseReviewHistory();
        history.setShowcaseId(showcase.getId());
        history.setSubmissionType(
                ShowcaseSubmissionType.INITIAL
        );
        history.setCategoryId(
                showcase.getCategory() != null
                        ? showcase.getCategory().getId()
                        : null
        );
        history.setTitle(showcase.getTitle());
        history.setOverview(showcase.getOverview());
        history.setCoverImageUrl(showcase.getCoverImageUrl());
        history.setLiveUrl(showcase.getLiveUrl());
        history.setRepoUrl(showcase.getRepoUrl());
        history.setVideoUrl(showcase.getVideoUrl());
        history.setReviewStatus(showcase.getReviewStatus());
        history.setSubmittedBy(showcase.getAuthor().getId());
        history.setSubmittedAt(submittedAt);
        history.setReviewedBy(showcase.getReviewedBy());
        history.setReviewedAt(showcase.getReviewedAt());
        history.setRejectionReason(
                showcase.getRejectionReason()
        );
        return history;
    }

    private ShowcaseReviewHistory createRevisionReviewHistory(
            ShowcaseRevision revision,
            LocalDateTime submittedAt
    ) {
        ShowcaseReviewHistory history =
                new ShowcaseReviewHistory();
        history.setShowcaseId(
                revision.getShowcase().getId()
        );
        history.setRevisionId(revision.getId());
        history.setSubmissionType(
                ShowcaseSubmissionType.REVISION
        );
        history.setCategoryId(
                revision.getCategory() != null
                        ? revision.getCategory().getId()
                        : null
        );
        history.setTitle(revision.getTitle());
        history.setOverview(revision.getOverview());
        history.setCoverImageUrl(revision.getCoverImageUrl());
        history.setLiveUrl(revision.getLiveUrl());
        history.setRepoUrl(revision.getRepoUrl());
        history.setVideoUrl(revision.getVideoUrl());
        history.setReviewStatus(revision.getReviewStatus());
        history.setSubmittedBy(revision.getSubmittedBy());
        history.setSubmittedAt(submittedAt);
        history.setReviewedBy(revision.getReviewedBy());
        history.setReviewedAt(revision.getReviewedAt());
        history.setRejectionReason(
                revision.getRejectionReason()
        );
        return history;
    }

    private ShowcaseReviewQueueItemResponse mapReviewQueueItem(
            ShowcaseReviewQueueProjection item
    ) {
        return new ShowcaseReviewQueueItemResponse(
                item.getShowcaseId(),
                item.getRevisionId(),
                ShowcaseSubmissionType.valueOf(
                        item.getSubmissionType()
                                .toUpperCase(Locale.ROOT)
                ),
                item.getAuthorId(),
                item.getAuthorName(),
                item.getCategoryId(),
                item.getCategoryName(),
                item.getTitle(),
                item.getOverview(),
                item.getCoverImageUrl(),
                item.getLiveUrl(),
                item.getRepoUrl(),
                item.getVideoUrl(),
                parseReviewStatus(item.getReviewStatus()),
                item.getSubmittedAt()
        );
    }

    private ReviewStatus parseReviewStatus(String databaseValue) {
        return switch (databaseValue.toLowerCase(Locale.ROOT)) {
            case "pending", "pending_approval" -> ReviewStatus.PENDING;
            case "approved" -> ReviewStatus.APPROVED;
            case "rejected" -> ReviewStatus.REJECTED;
            default -> throw new IllegalStateException(
                    "Unsupported showcase review status: "
                            + databaseValue
            );
        };
    }

    private void validatePagination(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0"
            );
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    private String validatePublishedSortProperty(String sortBy) {
        String property = sortBy == null || sortBy.isBlank()
                ? "createdAt"
                : sortBy.trim();
        Set<String> allowedProperties = Set.of(
                "createdAt",
                "updatedAt",
                "viewCount",
                "title"
        );

        if (!allowedProperties.contains(property)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sortBy must be createdAt, updatedAt, "
                            + "viewCount, or title."
            );
        }
        return property;
    }

    private Sort.Direction parseSortDirection(
            String sortDirection
    ) {
        String direction = sortDirection == null
                ? "DESC"
                : sortDirection.trim().toUpperCase(Locale.ROOT);

        try {
            return Sort.Direction.valueOf(direction);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sortDirection must be ASC or DESC.",
                    exception
            );
        }
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

}
