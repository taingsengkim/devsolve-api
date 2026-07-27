package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class ShowcaseStepMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "showcase", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract ShowcaseStep mapCreateShowcaseStepRequestToShowcaseStep(CreateShowcaseStepRequest request);

    public ShowcaseStepResponse mapShowcaseStepToShowcaseStepResponse(ShowcaseStep step) {
        return ShowcaseStepResponse.builder()
                .id(step.getId())
                .stepNumber(step.getStepNumber())
                .title(step.getTitle())
                .description(step.getDescription())
                .codeSnippet(step.getCodeSnippet())
                .imageUrl(step.getImageUrl())
                .diagramUrl(step.getDiagramUrl())
                .createdAt(step.getCreatedAt())
                .updatedAt(step.getUpdatedAt())
                .build();
    }
}
