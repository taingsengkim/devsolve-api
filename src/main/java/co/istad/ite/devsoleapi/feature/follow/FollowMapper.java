package co.istad.ite.devsoleapi.feature.follow;

import co.istad.ite.devsoleapi.feature.follow.dto.FollowRequest;
import co.istad.ite.devsoleapi.feature.follow.dto.FollowResponse;
import co.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface FollowMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "follower", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "followableType", source = "followableType")
    @Mapping(target = "followableId", source = "followableId")
    Follow toEntity(FollowRequest request);

    @Mapping(source = "follower.id", target = "followerId")
//    @Mapping(source = "follower.username", target = "followerUsername")
    @Mapping(source = "followableType", target = "followableType")
    @Mapping(source = "followableId", target = "followableId")
    @Mapping(target = "followableName", ignore = true) // You can set this dynamically
    @Mapping(source = "createdAt", target = "createdAt")
    FollowResponse toResponse(Follow follow);

    // If you need to update an existing follow
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "follower", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateFollowFromRequest(FollowRequest request, @MappingTarget Follow follow);
}