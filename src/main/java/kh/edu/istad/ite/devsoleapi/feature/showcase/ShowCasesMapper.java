package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ShowCasesMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "reviewStatus", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "viewCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "steps", ignore = true)
    public abstract ShowCases mapCreateShowCaseRequestToShowCase(
            CreateShowCasesRequest request
    );

    public ShowCasesResponse mapShowCaseToShowCaseResponse(
            ShowCases showCase,
            List<ShowcaseTagResponse> tags
    ) {
        return mapShowCase(showCase, tags, null);
    }

    public ShowCasesResponse mapShowCaseToDetailResponse(
            ShowCases showCase,
            List<ShowcaseTagResponse> tags,
            List<ShowcaseStepResponse> steps
    ) {
        return mapShowCase(showCase, tags, steps);
    }

    private ShowCasesResponse mapShowCase(
            ShowCases showCase,
            List<ShowcaseTagResponse> tags,
            List<ShowcaseStepResponse> steps
    ) {
        return ShowCasesResponse.builder()
                .id(showCase.getId())

                .authorId(
                        showCase.getAuthor() != null
                                ? String.valueOf(showCase.getAuthor().getId())
                                : null
                )

                .authorName(
                        showCase.getAuthor() != null
                                ? showCase.getAuthor().getFullName()
                                : null
                )

                .categoryId(
                        showCase.getCategory() != null
                                ? showCase.getCategory().getId()
                                : null
                )

                .categoryName(
                        showCase.getCategory() != null
                                ? showCase.getCategory().getName()
                                : null
                )

                .title(showCase.getTitle())
                .overview(showCase.getOverview())
                .coverImageUrl(showCase.getCoverImageUrl())
                .liveUrl(showCase.getLiveUrl())
                .repoUrl(showCase.getRepoUrl())
                .videoUrl(showCase.getVideoUrl())

                .reviewStatus(showCase.getReviewStatus())

                .viewCount(showCase.getViewCount())
                .createdAt(showCase.getCreatedAt())
                .updatedAt(showCase.getUpdatedAt())

                .tags(tags)
                .steps(steps)

                .build();
    }

    public ShowCasesSummaryResponse mapShowCaseToSummaryResponse(
            ShowCases showCase,
            List<ShowcaseTagResponse> tags
    ) {
        return ShowCasesSummaryResponse.builder()
                .id(showCase.getId())
                .authorId(
                        showCase.getAuthor() != null
                                ? String.valueOf(showCase.getAuthor().getId())
                                : null
                )
                .authorName(
                        showCase.getAuthor() != null
                                ? showCase.getAuthor().getFullName()
                                : null
                )
                .categoryId(
                        showCase.getCategory() != null
                                ? showCase.getCategory().getId()
                                : null
                )
                .categoryName(
                        showCase.getCategory() != null
                                ? showCase.getCategory().getName()
                                : null
                )
                .title(showCase.getTitle())
                .overview(showCase.getOverview())
                .coverImageUrl(showCase.getCoverImageUrl())
                .liveUrl(showCase.getLiveUrl())
                .repoUrl(showCase.getRepoUrl())
                .videoUrl(showCase.getVideoUrl())
                .reviewStatus(showCase.getReviewStatus())
                .hasUnpublishedRevision(false)
                .rejectionReason(showCase.getRejectionReason())
                .viewCount(showCase.getViewCount())
                .tags(tags)
                .createdAt(showCase.getCreatedAt())
                .updatedAt(showCase.getUpdatedAt())
                .build();
    }

    public ShowCasesResponse mapRevisionToShowCaseResponse(
            ShowcaseRevision revision,
            List<ShowcaseTagResponse> tags
    ) {
        ShowCases showcase = revision.getShowcase();

        return ShowCasesResponse.builder()
                .id(showcase.getId())
                .authorId(
                        showcase.getAuthor() != null
                                ? String.valueOf(showcase.getAuthor().getId())
                                : null
                )
                .authorName(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getFullName()
                                : null
                )
                .categoryId(
                        revision.getCategory() != null
                                ? revision.getCategory().getId()
                                : null
                )
                .categoryName(
                        revision.getCategory() != null
                                ? revision.getCategory().getName()
                                : null
                )
                .title(revision.getTitle())
                .overview(revision.getOverview())
                .coverImageUrl(revision.getCoverImageUrl())
                .liveUrl(revision.getLiveUrl())
                .repoUrl(revision.getRepoUrl())
                .videoUrl(revision.getVideoUrl())
                .reviewStatus(revision.getReviewStatus())
                .viewCount(showcase.getViewCount())
                .createdAt(revision.getCreatedAt())
                .updatedAt(revision.getUpdatedAt())
                .tags(tags)
                .steps(null)
                .build();
    }

    public ShowCasesSummaryResponse mapRevisionToSummaryResponse(
            ShowcaseRevision revision,
            List<ShowcaseTagResponse> tags
    ) {
        ShowCases showcase = revision.getShowcase();

        return ShowCasesSummaryResponse.builder()
                .id(showcase.getId())
                .authorId(
                        showcase.getAuthor() != null
                                ? String.valueOf(showcase.getAuthor().getId())
                                : null
                )
                .authorName(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getFullName()
                                : null
                )
                .categoryId(
                        revision.getCategory() != null
                                ? revision.getCategory().getId()
                                : null
                )
                .categoryName(
                        revision.getCategory() != null
                                ? revision.getCategory().getName()
                                : null
                )
                .title(revision.getTitle())
                .overview(revision.getOverview())
                .coverImageUrl(revision.getCoverImageUrl())
                .liveUrl(revision.getLiveUrl())
                .repoUrl(revision.getRepoUrl())
                .videoUrl(revision.getVideoUrl())
                .reviewStatus(revision.getReviewStatus())
                .hasUnpublishedRevision(true)
                .rejectionReason(revision.getRejectionReason())
                .viewCount(showcase.getViewCount())
                .tags(tags)
                .createdAt(revision.getCreatedAt())
                .updatedAt(revision.getUpdatedAt())
                .build();
    }

    public ShowcaseReviewDetailResponse mapShowCaseToReviewDetail(
            ShowCases showcase,
            List<ShowcaseTagResponse> tags,
            List<ShowcaseStepResponse> steps
    ) {
        return ShowcaseReviewDetailResponse.builder()
                .showcaseId(showcase.getId())
                .submissionType(ShowcaseSubmissionType.INITIAL)
                .authorId(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getId()
                                : null
                )
                .authorName(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getFullName()
                                : null
                )
                .categoryId(
                        showcase.getCategory() != null
                                ? showcase.getCategory().getId()
                                : null
                )
                .categoryName(
                        showcase.getCategory() != null
                                ? showcase.getCategory().getName()
                                : null
                )
                .title(showcase.getTitle())
                .overview(showcase.getOverview())
                .coverImageUrl(showcase.getCoverImageUrl())
                .liveUrl(showcase.getLiveUrl())
                .repoUrl(showcase.getRepoUrl())
                .videoUrl(showcase.getVideoUrl())
                .reviewStatus(showcase.getReviewStatus())
                .reviewedBy(showcase.getReviewedBy())
                .reviewedAt(showcase.getReviewedAt())
                .rejectionReason(showcase.getRejectionReason())
                .submittedAt(showcase.getUpdatedAt())
                .tags(tags)
                .steps(steps)
                .build();
    }

    public ShowcaseReviewDetailResponse mapRevisionToReviewDetail(
            ShowcaseRevision revision,
            List<ShowcaseTagResponse> tags,
            List<ShowcaseStepResponse> steps
    ) {
        ShowCases showcase = revision.getShowcase();

        return ShowcaseReviewDetailResponse.builder()
                .showcaseId(showcase.getId())
                .revisionId(revision.getId())
                .submissionType(ShowcaseSubmissionType.REVISION)
                .authorId(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getId()
                                : null
                )
                .authorName(
                        showcase.getAuthor() != null
                                ? showcase.getAuthor().getFullName()
                                : null
                )
                .categoryId(
                        revision.getCategory() != null
                                ? revision.getCategory().getId()
                                : null
                )
                .categoryName(
                        revision.getCategory() != null
                                ? revision.getCategory().getName()
                                : null
                )
                .title(revision.getTitle())
                .overview(revision.getOverview())
                .coverImageUrl(revision.getCoverImageUrl())
                .liveUrl(revision.getLiveUrl())
                .repoUrl(revision.getRepoUrl())
                .videoUrl(revision.getVideoUrl())
                .reviewStatus(revision.getReviewStatus())
                .reviewedBy(revision.getReviewedBy())
                .reviewedAt(revision.getReviewedAt())
                .rejectionReason(revision.getRejectionReason())
                .submittedAt(revision.getUpdatedAt())
                .tags(tags)
                .steps(steps)
                .build();
    }

    public ShowcaseReviewHistoryResponse mapReviewHistory(
            ShowcaseReviewHistory history
    ) {
        return ShowcaseReviewHistoryResponse.builder()
                .id(history.getId())
                .showcaseId(history.getShowcaseId())
                .revisionId(history.getRevisionId())
                .submissionType(history.getSubmissionType())
                .categoryId(history.getCategoryId())
                .title(history.getTitle())
                .overview(history.getOverview())
                .coverImageUrl(history.getCoverImageUrl())
                .liveUrl(history.getLiveUrl())
                .repoUrl(history.getRepoUrl())
                .videoUrl(history.getVideoUrl())
                .reviewStatus(history.getReviewStatus())
                .submittedBy(history.getSubmittedBy())
                .submittedAt(history.getSubmittedAt())
                .reviewedBy(history.getReviewedBy())
                .reviewedAt(history.getReviewedAt())
                .rejectionReason(history.getRejectionReason())
                .build();
    }
}
