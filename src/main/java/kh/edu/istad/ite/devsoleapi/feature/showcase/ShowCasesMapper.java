package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
            ShowCases showCase
    ) {

        return ShowCasesResponse.builder()
                .id(showCase.getId())

                .authorId(
                        showCase.getAuthor() != null
                                ? showCase.getAuthor().getId()
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

                .steps(null)

                .build();
    }
}