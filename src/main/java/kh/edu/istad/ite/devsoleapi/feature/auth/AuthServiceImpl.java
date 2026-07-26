package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    private final Keycloak keycloak;
    private final AuthMapper authMapper;
    private final KeycloakAdminProps props;
    private final UserProfileRepository userProfileRepository;
    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {

        if(!registerRequest.password().equals(registerRequest.confirmPassword())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password doesn't match!");
        }
        UsersResource userResource = keycloak.realm(props.getTargetRealm()).users();
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
        userRepresentation.setCredentials(List.of(credential));
        try(Response response = userResource.create(userRepresentation)) {
            log.info("Response status code : {}" , response.getStatus());
            if(response.getStatus() == HttpStatus.CREATED.value()){
                UserRepresentation createdUser = keycloak.realm(props.getTargetRealm()).users()
                        .search(userRepresentation.getUsername())
                        .getFirst();

                UserResource userResourceSet = keycloak.realm(props.getTargetRealm())
                        .users().get(createdUser.getId());
                userResourceSet.sendVerifyEmail();

                try {
                    RoleRepresentation roleUser = keycloak.realm(props.getTargetRealm())
                            .roles().get(RoleEnum.USER.name()).toRepresentation();
                    userResourceSet.roles().realmLevel().add(List.of(roleUser));
                } catch (Exception e) {
                    log.error("Role assignment failed: {}", e.getMessage(), e);
                    throw e;
                }

                UserProfile userProfile = new UserProfile();
                userProfile.setId(createdUser.getId());
                userProfile.setEmail(registerRequest.email());
                userProfile.setPhone(registerRequest.phone());
                userProfile.setFullName(registerRequest.firstName() + " " + registerRequest.lastName());
                userProfile.setStatus(UserStatus.ACTIVE);
                userProfileRepository.save(userProfile);

                return authMapper.toRegisterResponse(registerRequest,createdUser);
            }else if (response.getStatus() == HttpStatus.CONFLICT.value()){
                log.info("Check username or email already exist");
            }
        }
        return null;
    }
}
