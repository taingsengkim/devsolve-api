package co.istad.ite.devsoleapi.feature.userprofile;

import co.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public abstract class UserProfileMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void mapUpdateUserProfileRequestToUserProfile(
            UpdateUserProfileRequest updateUserProfileRequest,
            @MappingTarget UserProfile userProfile
    );

    public void mapUpdateUserProfileRequestToUserRepresentation(
            UpdateUserProfileRequest updateUserProfileRequest,
            @MappingTarget UserRepresentation userRepresentation
    ) {
        if (updateUserProfileRequest.email() != null)
            userRepresentation.setFirstName(updateUserProfileRequest.email());

        if (updateUserProfileRequest.fullName() != null)
            userRepresentation.setLastName(updateUserProfileRequest.fullName());

        if (updateUserProfileRequest.biography() != null)
            userRepresentation.setLastName(updateUserProfileRequest.biography());

        if (updateUserProfileRequest.phone() != null)
            userRepresentation.setLastName(updateUserProfileRequest.phone());

        if (updateUserProfileRequest.avatarUrl() != null)
            userRepresentation.setLastName(updateUserProfileRequest.avatarUrl());
    }

    public UserProfileResponse toUserProfileResponse(UserRepresentation userRepresentation, UserProfile userProfile) {
        return UserProfileResponse.builder()
                .id(userRepresentation.getId())
                .fullName(userRepresentation.getFirstName() + " " + userRepresentation.getLastName())
                .email(userRepresentation.getEmail())
                .phone(userProfile.getPhone())
                .biography(userProfile.getBiography())
                .status(userProfile.getStatus())
                .avatarUrl(userProfile.getAvatarUrl())
                .dateOfBirth(userProfile.getDateOfBirth())
                .build();
    }
}
