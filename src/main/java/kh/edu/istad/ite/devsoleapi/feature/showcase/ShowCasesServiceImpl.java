package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShowCasesServiceImpl implements ShowCasesService {
    private final ShowCasesRepository showCaseRepository;
    private final UserProfileRepository userProfileRepository;
    private final ShowCasesMapper showCasesMapper;
    private final CategoryRepository categoryRepository;

    @Override
    public Page<ShowCasesResponse> getAllPublished(int pageNumber, int pageSize) {

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return showCaseRepository
                .findByReviewStatusAndDeletedAtIsNull(
                        ReviewStatus.APPROVED,
                        pageable
                )
                .map(showCasesMapper::mapShowCaseToShowCaseResponse);
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

        return showCasesMapper.mapShowCaseToShowCaseResponse(showcase);
    }

    @Override
    public ShowCasesResponse create(CreateShowCasesRequest request) {
        String authorId = AuthUtils.extractUserId();

        UserProfile author = userProfileRepository
                .findById(UUID.fromString(authorId))
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "User not found."));

        Category category =
                categoryRepository.findById(request.categoryId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Category not found."));

        if (showCaseRepository.existsByAuthor_IdAndTitleAndDeletedAtIsNull(
                authorId,
                request.title())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have a showcase with this title.");
        }

        if (request.repoUrl() != null &&
                showCaseRepository.existsByRepoUrl(request.repoUrl())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Repository URL already exists.");
        }

        if (request.liveUrl() != null &&
                showCaseRepository.existsByLiveUrl(request.liveUrl())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Live URL already exists.");
        }

        ShowCases showCase = showCasesMapper.mapCreateShowCaseRequestToShowCase(request);

        showCase.setAuthor(author);
        showCase.setCategory(category);

        showCase.setReviewStatus(ReviewStatus.PENDING);
        showCase.setViewCount(0);

        ShowCases saved = showCaseRepository.save(showCase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved);
    }

    @Override
    public ShowCasesResponse update(UUID showcaseId, UpdateShowCasesRequest request) {
        String authorId = AuthUtils.extractUserId();

        ShowCases showCase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        // Check ownership
        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own showcase."
            );
        }

        // Update category
        if (request.categoryId() != null) {

            Category category = categoryRepository
                    .findById(request.categoryId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Category not found."
                            )
                    );

            showCase.setCategory(category);
        }

        // Check duplicate title
        if (request.title() != null &&
                showCaseRepository
                        .existsByAuthor_IdAndTitleAndIdNotAndDeletedAtIsNull(
                                authorId,
                                request.title(),
                                showcaseId
                        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have another showcase with this title."
            );
        }

        // Update fields
        if (request.title() != null) {
            showCase.setTitle(request.title());
        }

        if (request.overview() != null) {
            showCase.setOverview(request.overview());
        }

        if (request.coverImageUrl() != null) {
            showCase.setCoverImageUrl(request.coverImageUrl());
        }

        if (request.liveUrl() != null) {
            showCase.setLiveUrl(request.liveUrl());
        }

        if (request.repoUrl() != null) {
            showCase.setRepoUrl(request.repoUrl());
        }

        if (request.videoUrl() != null) {
            showCase.setVideoUrl(request.videoUrl());
        }

        /*
         * When author edits the showcase,
         * it must be reviewed again by admin.
         */
        showCase.setReviewStatus(ReviewStatus.PENDING);

        showCase.setReviewedBy(null);
        showCase.setReviewedAt(null);
        showCase.setRejectionReason(null);

        ShowCases saved = showCaseRepository.save(showCase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved);
    }

    @Override
    public void softDelete(UUID showcaseId) {
        String authorId = AuthUtils.extractUserId();

        ShowCases showCase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."));

        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own showcase.");
        }

        showCase.setDeletedAt(LocalDateTime.now());

        showCaseRepository.save(showCase);
    }

    @Override
    public void hardDelete(UUID showcaseId) {
        String authorId = AuthUtils.extractUserId();

        ShowCases showCase = showCaseRepository
                .findById(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."));

        if (!showCase.getAuthor().getId().equals(authorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own showcase.");
        }
        showCaseRepository.delete(showCase);
    }

//    update status by admin
    @Override
    public ShowCasesResponse updateStatus(UUID showcaseId, UpdateShowcaseStatusRequest request) {
        ShowCases showcase = showCaseRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        )
                );

        ReviewStatus reviewStatus = request.reviewStatus();

        showcase.setReviewStatus(reviewStatus);

        if (reviewStatus == ReviewStatus.APPROVED) {

            showcase.setReviewedAt(LocalDateTime.now());
            showcase.setRejectionReason(null);

        } else if (reviewStatus == ReviewStatus.REJECTED) {

            showcase.setReviewedAt(LocalDateTime.now());

        } else if (reviewStatus == ReviewStatus.PENDING) {

            showcase.setReviewedAt(null);
            showcase.setRejectionReason(null);
        }

        ShowCases saved = showCaseRepository.save(showcase);

        return showCasesMapper.mapShowCaseToShowCaseResponse(saved);
    }

}
