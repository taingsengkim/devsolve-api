package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
@Mapper(componentModel = "spring")
public abstract class ModerationActionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "admin", ignore = true)
    @Mapping(target = "targetType", ignore = true)
    @Mapping(target = "targetId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract ModerationAction
    mapCreateModerationActionRequestToModerationAction(
            CreateModerationActionRequest request
    );

    @Mapping(target = "adminId", source = "admin.id")
    @Mapping(target = "adminName", source = "admin.fullName")
    public abstract ModerationActionResponse
    mapModerationActionToModerationActionResponse(
            ModerationAction moderationAction
    );
}
