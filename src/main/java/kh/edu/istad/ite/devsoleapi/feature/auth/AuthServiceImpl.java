package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final Keycloak keycloak;
    private final AuthMapper authMapper;
    private final KeycloakAdminProps props;
    private final UserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (!registerRequest.password().equals(registerRequest.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        if (registerRequest.accountType() == RoleEnum.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN accounts cannot be created through public registration"
            );
        }
        if (registerRequest.accountType() == RoleEnum.COMPANY) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Company accounts must register through /api/v1/organizations/register"
            );
        }

        UsersResource usersResource = keycloak.realm(props.getTargetRealm()).users();
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(registerRequest.username());
        userRepresentation.setEmail(registerRequest.email());
        userRepresentation.setFirstName(registerRequest.firstName());
        userRepresentation.setLastName(registerRequest.lastName());

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(registerRequest.password());

        userRepresentation.setEnabled(true);
        userRepresentation.setEmailVerified(false);
        userRepresentation.setRequiredActions(List.of("VERIFY_EMAIL"));
        userRepresentation.setCredentials(List.of(credential));

        try (Response response = usersResource.create(userRepresentation)) {
            log.info("Keycloak registration response status: {}", response.getStatus());

            if (response.getStatus() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Username or email already exists"
                );
            }

            if (response.getStatus() != HttpStatus.CREATED.value()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Identity provider could not create the user"
                );
            }

            String createdUserId = CreatedResponseUtil.getCreatedId(response);
            UserResource createdUserResource = usersResource.get(createdUserId);

            try {
                RoleRepresentation userRole = keycloak.realm(props.getTargetRealm())
                        .roles()
                        .get(registerRequest.accountType().name())
                        .toRepresentation();
                createdUserResource.roles().realmLevel().add(List.of(userRole));

                UserProfile userProfile = new UserProfile();
                userProfile.setId(UUID.fromString(createdUserId));
                userProfile.setEmail(registerRequest.email());
                userProfile.setPhone(registerRequest.phone());
                userProfile.setFullName(
                        registerRequest.firstName().trim() + " " + registerRequest.lastName().trim()
                );
                userProfile.setStatus(UserStatus.ACTIVE);
                userProfileRepository.saveAndFlush(userProfile);

                createdUserResource.sendVerifyEmail();
                UserRepresentation createdUser = createdUserResource.toRepresentation();
                return authMapper.toRegisterResponse(registerRequest, createdUser);
            } catch (RuntimeException exception) {
                deleteKeycloakUserAfterFailedRegistration(createdUserResource, createdUserId);
                throw exception;
            }
        }
    }

    private void deleteKeycloakUserAfterFailedRegistration(
            UserResource createdUserResource,
            String createdUserId
    ) {
        try {
            createdUserResource.remove();
        } catch (RuntimeException cleanupException) {
            log.error(
                    "Failed to remove Keycloak user {} after local registration failed",
                    createdUserId,
                    cleanupException
            );
        }
    }
}
