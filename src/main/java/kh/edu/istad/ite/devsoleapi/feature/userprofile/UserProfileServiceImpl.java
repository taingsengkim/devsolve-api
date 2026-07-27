package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final Keycloak keycloak;
    private final KeycloakAdminProps keycloakAdminProps;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse me() {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);
        UserRepresentation keycloakUser = findKeycloakUser(userId).toRepresentation();
        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UpdateUserProfileRequest request) {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);
        UserResource keycloakUserResource = findKeycloakUser(userId);
        UserRepresentation keycloakUser = keycloakUserResource.toRepresentation();

        userProfileMapper.mapUpdateUserProfileRequestToUserProfile(request, userProfile);

        if (request.firstName() != null || request.lastName() != null) {
            String firstName = request.firstName() != null
                    ? request.firstName().trim()
                    : keycloakUser.getFirstName();
            String lastName = request.lastName() != null
                    ? request.lastName().trim()
                    : keycloakUser.getLastName();

            keycloakUser.setFirstName(firstName);
            keycloakUser.setLastName(lastName);
            userProfile.setFullName(buildFullName(firstName, lastName));

            userProfileRepository.saveAndFlush(userProfile);
            keycloakUserResource.update(keycloakUser);
        }

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));
    }

    private UserResource findKeycloakUser(UUID userId) {
        return keycloak.realm(keycloakAdminProps.getTargetRealm())
                .users()
                .get(userId.toString());
    }

    private String buildFullName(String firstName, String lastName) {
        return String.join(
                " ",
                firstName == null ? "" : firstName,
                lastName == null ? "" : lastName
        ).trim();
    }
}
