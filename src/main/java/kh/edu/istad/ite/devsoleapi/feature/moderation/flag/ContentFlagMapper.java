package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * The write side only.
 *
 * <p>A flag no longer maps to its response here. Every read goes through
 * {@link FlagQueueRepository}, because the response carries the reported
 * content — which lives in one of five tables that no association relates to a
 * flag, and which a mapper over the entity cannot reach. One path rather than
 * two: see {@link FlagRowAssembler}.
 */
@Mapper(componentModel = "spring")
public abstract class ContentFlagMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "resolutionNote", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract ContentFlag mapCreateFlagRequestToContentFlag(
            CreateFlagRequest request
    );
}
