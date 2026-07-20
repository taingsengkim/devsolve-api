package co.istad.ite.devsoleapi.feature.userprofile;

import co.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import co.istad.ite.devsoleapi.config.security.AuthUtils;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService{
    private final Keycloak keycloak;
    private final KeycloakAdminProps props;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    @Override
    public UserProfileResponse me() {
        String userId = AuthUtils.extractUserId();
        UserRepresentation keycloakUser = keycloak.realm(props.getTargetRealm())
                .users()
                .get(userId)
                .toRepresentation();

        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile has not been found"));

        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);    }
}
