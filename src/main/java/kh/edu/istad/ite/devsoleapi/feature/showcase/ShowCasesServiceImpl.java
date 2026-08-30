package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
import kh.edu.istad.ite.devsoleapi.common.listing.ViewCountGuard;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;
import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewQueueItemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagService;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepMapper;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
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
    private final FollowNotificationService followNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageStorageService imageStorageService;
    private final ShowcaseTagService showcaseTagService;
    private final ShowcaseCommentCounts showcaseCommentCounts;
    private final ViewCountGuard viewCountGuard;
    private final ShowcaseDetailCache showcaseDetailCache;

    @Override
    @Transactional(readOnly = true)
    public Page<ShowCasesSummaryResponse> getAllPublished(
            String query,
            UUID categoryId,
            String tag,
            ListingSort sort,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);

        ListingSort effectiveSort = sort == null ? ListingSort.NEWEST : sort;
        String queryPattern = containsPattern(normalizeQuery(query));
        String tagSlug = normalizeQuery(tag);

        // One query shape for every filter combination. The old code branched
        // between three repository methods depending on which filters were
        // present, which is why adding a fourth filter meant adding branches
        // rather than a parameter.
        Page<ShowCases> showcases = effectiveSort.isScoreOrdered()
                ? showCaseRepository.searchPublishedByScore(
                        ReviewStatus.APPROVED,
                        queryPattern,
                        categoryId,
                        tagSlug,
                        effectiveSort.windowStart(),
                        VoteType.SHOWCASE,
                        PageRequest.of(pageNumber, pageSize)
                )
                : showCaseRepository.searchPublished(
                        ReviewStatus.APPROVED,
                        queryPattern,
                        categoryId,
                        tagSlug,
                        PageRequest.of(pageNumber, pageSize, columnSort(
                                effectiveSort
                        ))
                );

        Map<UUID, List<ShowcaseTagResponse>> tagsByShowcaseId =
                showcaseTagService.tagsOfShowcases(idsOf(showcases));

        return showcaseCommentCounts.applyToSummaries(showcases.map(showcase ->
                showCasesMapper.mapShowCaseToSummaryResponse(
                        showcase,
                        tagsByShowcaseId.getOrDefault(
                                showcase.getId(),
                                List.of()
                        )
                )));
    }

    /**
     * The orderings a showcase row can express by itself, always with the id
     * as a final tiebreaker. Without it two showcases created in the same
     * instant have no defined order, and an offset page boundary landing
     * between them shows one twice and drops the other.
     */
    private Sort columnSort(ListingSort sort) {
        Sort primary = switch (sort) {
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case MOST_VIEWED -> Sort.by(Sort.Direction.DESC, "viewCount")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case TITLE -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        return primary.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private List<UUID> idsOf(Page<ShowCases> showcases) {
        return showcases.getContent()
                .stream()
                .map(ShowCases::getId)
                .toList();
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
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<ShowCases> showcases = showCaseRepository
                .findByAuthor_IdAndDeletedAtIsNull(
                        authorId,
                        pageable
                );

        if (showcases.isEmpty()) {
            return showcases.map(showcase ->
                    showCasesMapper.mapShowCaseToSummaryResponse(
                            showcase,
                            List.of()
                    ));
        }

        Map<UUID, ShowcaseRevision> revisionsByShowcaseId =
                showcaseRevisionRepository
                        .findByShowcase_IdIn(idsOf(showcases))
                        .stream()
                        .collect(Collectors.toMap(
                                revision -> revision
                                        .getShowcase()
                                        .getId(),
                                Function.identity()
                        ));

        // A summary shows the revision's content when there is one, so its
        // tags have to come from the same side.
        Map<UUID, List<ShowcaseTagResponse>> tagsByShowcaseId =
                showcaseTagService.tagsOfShowcases(idsOf(showcases));
        Map<UUID, List<ShowcaseTagResponse>> tagsByRevisionId =
                showcaseTagService.tagsOfRevisions(
                        revisionsByShowcaseId.values()
                                .stream()
                                .map(ShowcaseRevision::getId)
                                .toList()
                );

        return showcaseCommentCounts.applyToSummaries(showcases.map(showcase -> {
            ShowcaseRevision revision =
                    revisionsByShowcaseId.get(showcase.getId());

            return revision == null
                    ? showCasesMapper
                            .mapShowCaseToSummaryResponse(
                                    showcase,
                                    tagsByShowcaseId.getOrDefault(
                                            showcase.getId(),
                                            List.of()
                                    )
                            )
                    : showCasesMapper
                            .mapRevisionToSummaryResponse(
                                    revision,
                                    tagsByRevisionId.getOrDefault(
                                            revision.getId(),
                                            List.of()
                                    )
                            );
        }));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowCasesSummaryResponse> getPublishedByAuthor(
            UUID authorId,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);
        Page<ShowCases> showcases = showCaseRepository
                .findByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
                        authorId,
                        ReviewStatus.APPROVED,
                        PageRequest.of(
                                pageNumber,
                                pageSize,
                                Sort.by(Sort.Direction.DESC, "createdAt")
                                        .and(Sort.by(
                                                Sort.Direction.DESC,
                                                "id"
                                        ))
                        )
                );

        Map<UUID, List<ShowcaseTagResponse>> tagsByShowcaseId =
                showcaseTagService.tagsOfShowcases(idsOf(showcases));

        return showcaseCommentCounts.applyToSummaries(showcases.map(showcase ->
                showCasesMapper.mapShowCaseToSummaryResponse(
                        showcase,
                        tagsByShowcaseId.getOrDefault(
                                showcase.getId(),
                                List.of()
                        )
                )));
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

        Page<ShowcaseReviewQueueProjection> queue = showCaseRepository
                .findReviewQueue(reviewStatus.name(), pageable);

        // A queued revision is reviewed on its own content, an initial
        // submission on the showcase's.
        Map<UUID, List<ShowcaseTagResponse>> tagsByShowcaseId =
                showcaseTagService.tagsOfShowcases(
                        queue.getContent()
                                .stream()
                                .filter(item -> item.getRevisionId() == null)
                                .map(ShowcaseReviewQueueProjection
                                        ::getShowcaseId)
                                .toList()
                );
        Map<UUID, List<ShowcaseTagResponse>> tagsByRevisionId =
                showcaseTagService.tagsOfRevisions(
                        queue.getContent()
                                .stream()
                                .map(ShowcaseReviewQueueProjection
                                        ::getRevisionId)
                                .filter(Objects::nonNull)
                                .toList()
                );

        return queue.map(item -> mapReviewQueueItem(
                item,
                item.getRevisionId() == null
                        ? tagsByShowcaseId.getOrDefault(
                                item.getShowcaseId(),
                                List.of()
                        )
                        : tagsByRevisionId.getOrDefault(
                                item.getRevisionId(),
                                List.of()
                        )
        ));
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
                    showcaseTagService.tagsOfShowcase(showcaseId),
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
                showcaseTagService.tagsOfRevision(revision.getId()),
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
                showcaseTagService.tagsOfRevision(revision.getId()),
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

        // The showcase row above is read fresh every time; only its tags and
        // steps come from the cache, so a soft delete or a moderation change
        // still takes effect on the next request.
        ShowcaseDetailParts parts = showcaseDetailCache.load(id);

        return showcaseCommentCounts.applyToDetail(
                showCasesMapper.mapShowCaseToDetailResponse(
                        showcase,
                        parts.tags(),
                        parts.steps()
                )
        );
    }

    @Override
    @Transactional
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

        return showCasesMapper.mapShowCaseToShowCaseResponse(
                saved,
                showcaseTagService.replaceShowcaseTags(
                        saved,
                        request.tagIds(),
                        request.tags()
                )
        );
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
    public ShowCasesResponse update(
            UUID showcaseId,
            UpdateShowCasesRequest request
    ) {
        UUID authorId = extractCurrentUserId();

        ShowCases showCase = findOwnShowcase(showcaseId, authorId);

        validateUniqueTitle(
                authorId,
                showcaseId,
                request.title()
        );

        if (showCase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showCase, authorId);

            applyUpdate(revision, request);
            List<ShowcaseTagResponse> tags = requestsTagChange(request)
                    ? showcaseTagService.replaceRevisionTags(
                            revision,
                            request.tagIds(),
                            request.tags()
                    )
                    : showcaseTagService.tagsOfRevision(revision.getId());
            showcaseRevisionWorkflow.submit(revision, authorId);

            return showCasesMapper.mapRevisionToShowCaseResponse(
                    revision,
                    tags
            );
        }

        applyUpdate(showCase, request);
        List<ShowcaseTagResponse> tags = requestsTagChange(request)
                ? showcaseTagService.replaceShowcaseTags(
                        showCase,
                        request.tagIds(),
                        request.tags()
                )
                : showcaseTagService.tagsOfShowcase(showcaseId);
        resubmitForReview(showCase);

        ShowCases saved =
                showCaseRepository.save(showCase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved, tags);
    }

    /**
     * Tags are only touched when the request mentions them: an update that
     * leaves both fields out keeps whatever is already there.
     */
    private boolean requestsTagChange(UpdateShowCasesRequest request) {
        return request.tagIds() != null || request.tags() != null;
    }

    /**
     * Stores {@code file} as the showcase cover.
     *
     * <p>A published showcase routes the new cover into its revision so the
     * change is moderated like any other edit, which means the live cover has
     * to survive until that revision is approved.
     */
    @Override
    @Transactional
    public ShowCasesResponse uploadCoverImage(
            UUID showcaseId,
            MultipartFile file
    ) {
        UUID authorId = extractCurrentUserId();
        ShowCases showcase = findOwnShowcase(showcaseId, authorId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showcase, authorId);

            revision.setCoverImageUrl(imageStorageService.replace(
                    coverImagePrefix(showcaseId),
                    ImageStorageService.supersededUrl(
                            revision.getCoverImageUrl(),
                            showcase.getCoverImageUrl()
                    ),
                    file
            ));
            showcaseRevisionWorkflow.submit(revision, authorId);

            return showCasesMapper.mapRevisionToShowCaseResponse(
                    revision,
                    showcaseTagService.tagsOfRevision(revision.getId())
            );
        }

        showcase.setCoverImageUrl(imageStorageService.replace(
                coverImagePrefix(showcaseId),
                showcase.getCoverImageUrl(),
                file
        ));
        resubmitForReview(showcase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(
                showCaseRepository.save(showcase),
                showcaseTagService.tagsOfShowcase(showcaseId)
        );
    }

    @Override
    @Transactional
    public ShowCasesResponse removeCoverImage(UUID showcaseId) {
        UUID authorId = extractCurrentUserId();
        ShowCases showcase = findOwnShowcase(showcaseId, authorId);

        if (showcase.getReviewStatus() == ReviewStatus.APPROVED) {
            ShowcaseRevision revision = showcaseRevisionWorkflow
                    .getOrCreate(showcase, authorId);

            imageStorageService.remove(
                    ImageStorageService.supersededUrl(
                            revision.getCoverImageUrl(),
                            showcase.getCoverImageUrl()
                    )
            );
            revision.setCoverImageUrl(null);
            showcaseRevisionWorkflow.submit(revision, authorId);

            return showCasesMapper.mapRevisionToShowCaseResponse(
                    revision,
                    showcaseTagService.tagsOfRevision(revision.getId())
            );
        }

        imageStorageService.remove(showcase.getCoverImageUrl());
        showcase.setCoverImageUrl(null);
        resubmitForReview(showcase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(
                showCaseRepository.save(showcase),
                showcaseTagService.tagsOfShowcase(showcaseId)
        );
    }

    private String coverImagePrefix(UUID showcaseId) {
        return "showcases/" + showcaseId + "/cover";
    }

    private void resubmitForReview(ShowCases showcase) {
        showcase.setReviewStatus(ReviewStatus.PENDING);
        showcase.setReviewedBy(null);
        showcase.setReviewedAt(null);
        showcase.setRejectionReason(null);
    }

    private ShowCases findOwnShowcase(
            UUID showcaseId,
            UUID authorId
    ) {
        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        if (!showcase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own showcase."
            );
        }
        return showcase;
    }

    /**
     * Deletes the stored images in {@code candidates} that nothing points at
     * any more. Externally hosted URLs are left alone by the storage service.
     */
    private void deleteUnreferencedImages(
            Collection<String> candidates,
            Collection<String> stillReferenced
    ) {
        candidates.stream()
                .filter(url -> !stillReferenced.contains(url))
                .distinct()
                .forEach(imageStorageService::remove);
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
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
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

        List<String> orphanedImages = new ArrayList<>(
                showcaseRevisionWorkflow.imageUrlsOf(showCase)
        );
        showcaseRevisionRepository
                .findByShowcase_Id(showcaseId)
                .ifPresent(revision -> {
                    orphanedImages.addAll(
                            showcaseRevisionWorkflow.imageUrlsOf(revision)
                    );
                    showcaseRevisionWorkflow.discard(revision);
                });

        // Steps, revisions and tag links hold a non-null FK to the showcase,
        // so their deletes have to reach the database before the parent's.
        showcaseTagService.deleteShowcaseTags(showCase);
        showcaseStepRepository.deleteByShowcase_Id(showcaseId);
        showCaseRepository.flush();

        showCaseRepository.delete(showCase);
        deleteUnreferencedImages(orphanedImages, List.of());
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
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

        List<String> revisionImages =
                showcaseRevisionWorkflow.imageUrlsOf(revision);
        List<String> publishedImages =
                showcaseRevisionWorkflow.imageUrlsOf(showcase);

        showcaseRevisionWorkflow.discard(revision);
        deleteUnreferencedImages(revisionImages, publishedImages);
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
        // A repeat view inside the window still answers with the current
        // count, so the client cannot tell the difference and does not need
        // to. Only the increment is skipped.
        if (!viewCountGuard.shouldCount("showcase", showcaseId)) {
            Integer current = showCaseRepository
                    .findViewCountById(showcaseId);
            if (current == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Showcase not found."
                );
            }
            return new ShowcaseViewCountResponse(showcaseId, current);
        }

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
    // Approving a revision replaces the showcase's steps and tags.
    @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, key = "#showcaseId")
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

        if (saved.getReviewStatus() == ReviewStatus.APPROVED) {
            followNotificationService.notifyFollowers(
                    FollowType.USER,
                    saved.getAuthor().getId(),
                    saved.getAuthor().getId(),
                    "New showcase published",
                    saved.getTitle(),
                    NotificationType.SHOWCASE,
                    saved.getId(),
                    "showcase-published:" + saved.getId()
            );
        }

        // The author is not among their own followers, and a rejection has no
        // broadcast at all — without this the person who submitted it is the
        // one person the decision never reaches. Keyed on the review time,
        // since a rejected showcase can be corrected and resubmitted.
        eventPublisher.publishEvent(NotificationEvent.to(
                saved.getAuthor() == null ? null : saved.getAuthor().getId(),
                saved.getReviewStatus() == ReviewStatus.APPROVED
                        ? "Your showcase was published"
                        : "Your showcase needs changes",
                saved.getReviewStatus() == ReviewStatus.APPROVED
                        ? "\"" + saved.getTitle() + "\" is now live."
                        : "\"" + saved.getTitle() + "\" was not approved: "
                                + saved.getRejectionReason(),
                NotificationType.SHOWCASE,
                saved.getId(),
                "showcase:" + saved.getId() + ":reviewed:" + reviewedAt
        ));

        return showCasesMapper.mapShowCaseToShowCaseResponse(
                saved,
                showcaseTagService.tagsOfShowcase(saved.getId())
        );
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

            eventPublisher.publishEvent(NotificationEvent.to(
                    showcase.getAuthor() == null
                            ? null
                            : showcase.getAuthor().getId(),
                    "Your showcase edit needs changes",
                    "Your changes to \"" + showcase.getTitle()
                            + "\" were not approved: "
                            + rejected.getRejectionReason()
                            + " The published version is unaffected.",
                    NotificationType.SHOWCASE,
                    showcase.getId(),
                    "showcase-revision:" + rejected.getId() + ":rejected"
            ));

            return showCasesMapper.mapRevisionToShowCaseResponse(
                    rejected,
                    showcaseTagService.tagsOfRevision(rejected.getId())
            );
        }

        LocalDateTime submittedAt = revision.getUpdatedAt();
        LocalDateTime reviewedAt = LocalDateTime.now();
        revision.setReviewStatus(ReviewStatus.APPROVED);
        revision.setReviewedBy(reviewerId);
        revision.setReviewedAt(reviewedAt);
        revision.setRejectionReason(null);

        // Both sides have to be read before promotion overwrites the
        // published record with the revision's images.
        List<String> supersededImages =
                showcaseRevisionWorkflow.imageUrlsOf(showcase);
        List<String> promotedImages =
                showcaseRevisionWorkflow.imageUrlsOf(revision);

        showcaseRevisionWorkflow.promoteSteps(showcase, revision);
        List<ShowcaseTagResponse> promotedTags =
                showcaseTagService.promoteTags(showcase, revision);
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
        followNotificationService.notifyFollowers(
                FollowType.SHOWCASE,
                saved.getId(),
                saved.getAuthor().getId(),
                "Showcase updated",
                saved.getTitle() + " has a newly approved revision.",
                NotificationType.SHOWCASE,
                saved.getId(),
                "showcase-revision-approved:" + revision.getId()
        );

        eventPublisher.publishEvent(NotificationEvent.to(
                saved.getAuthor() == null ? null : saved.getAuthor().getId(),
                "Your showcase edit was published",
                "Your changes to \"" + saved.getTitle() + "\" are now live.",
                NotificationType.SHOWCASE,
                saved.getId(),
                "showcase-revision:" + revision.getId() + ":approved"
        ));

        showcaseRevisionWorkflow.discard(revision);
        deleteUnreferencedImages(supersededImages, promotedImages);

        return showCasesMapper.mapShowCaseToShowCaseResponse(
                saved,
                promotedTags
        );
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
            ShowcaseReviewQueueProjection item,
            List<ShowcaseTagResponse> tags
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
                tags,
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
        return query.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Null in, null out: the query treats a null pattern as "no text filter"
     * rather than making the caller pick a different repository method.
     */
    private String containsPattern(String normalizedQuery) {
        return normalizedQuery == null ? null : "%" + normalizedQuery + "%";
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
