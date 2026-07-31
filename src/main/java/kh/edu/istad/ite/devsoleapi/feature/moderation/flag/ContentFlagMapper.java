package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class ContentFlagMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "resolutionNote", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract ContentFlag mapCreateFlagRequestToContentFlag(
            CreateFlagRequest request
    );

    @Mapping(target = "reporterId", source = "reporter.id")
    @Mapping(target = "reporterName", source = "reporter.fullName")
    @Mapping(target = "reviewedBy", source = "reviewedBy.id")
    public abstract FlagResponse mapContentFlagToFlagResponse(
            ContentFlag flag
    );
}
