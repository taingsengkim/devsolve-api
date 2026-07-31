package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.SocialLinkResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public abstract class UserProfileMapper {
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "fullName", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reputation", ignore = true)
    @Mapping(target = "totalReports", ignore = true)
    @Mapping(target = "validReports", ignore = true)
    @Mapping(target = "criticalReports", ignore = true)
    @Mapping(target = "recognitionCount", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "socialLinks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void mapUpdateUserProfileRequestToUserProfile(
            UpdateUserProfileRequest updateUserProfileRequest,
            @MappingTarget UserProfile userProfile
    );

    public UserProfileResponse toUserProfileResponse(
            UserRepresentation keycloakUser,
            UserProfile userProfile
    ) {
        return UserProfileResponse.builder()
                .id(userProfile.getId())
                .firstName(keycloakUser.getFirstName())
                .lastName(keycloakUser.getLastName())
                .fullName(userProfile.getFullName())
                .email(keycloakUser.getEmail())
                .phone(userProfile.getPhone())
                .biography(userProfile.getBiography())
                .status(userProfile.getStatus())
                .avatarUrl(userProfile.getAvatarUrl())
                .dateOfBirth(userProfile.getDateOfBirth())
                .gender(userProfile.getGender())
                .country(userProfile.getCountry())
                .socialLinks(toSocialLinkResponses(userProfile))
                .reputation(userProfile.getReputation())
                .totalReports(userProfile.getTotalReports())
                .validReports(userProfile.getValidReports())
                .criticalReports(userProfile.getCriticalReports())
                .recognitionCount(userProfile.getRecognitionCount())
                .lastLoginAt(userProfile.getLastLoginAt())
                .createdAt(userProfile.getCreatedAt())
                .updatedAt(userProfile.getUpdatedAt())
                .build();
    }

    public List<SocialLinkResponse> toSocialLinkResponses(
            UserProfile userProfile
    ) {
        return userProfile.getSocialLinks().stream()
                .sorted(Comparator.comparing(link ->
                        link.getPlatform().name()))
                .map(link -> new SocialLinkResponse(
                        link.getPlatform(),
                        link.getUrl()
                ))
                .toList();
    }
}
