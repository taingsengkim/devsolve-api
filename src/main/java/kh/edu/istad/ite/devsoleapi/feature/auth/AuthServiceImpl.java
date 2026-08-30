package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.SocialSyncResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UsernamePolicy;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserProfileService;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserProvisioningService userProvisioningService;
    private final UserProfileService userProfileService;
    private final RegistrationRateLimiter registrationRateLimiter;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        registrationRateLimiter.check();

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

        // Checked before the identity is created, so a taken handle fails
        // without leaving a Keycloak user behind to roll back.
        String username = registerRequest.username().trim();
        if (UsernamePolicy.isReserved(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "That username is reserved"
            );
        }
        if (userProfileRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "That username is already taken"
            );
        }

        UsersResource usersResource = keycloak.realm(props.getTargetRealm()).users();
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setUsername(username);
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
                userProfile.setUsername(username);
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

    /**
     * The social-login counterpart to {@link #register}: makes the caller's
     * local profile exist and hands back the same shape the client would have
     * got from registering.
     *
     * <p>{@link kh.edu.istad.ite.devsoleapi.config.security.UserProvisioningFilter}
     * already attempts this on every
     * authenticated request, so a working flow finds the row present and this
     * call is a cheap read. Its value is in the flow that is <em>not</em>
     * working: the filter can only log its skips, whereas here each one becomes
     * a status code the frontend can show, which is what makes "the social user
     * never reached the database" diagnosable from the client side.
     */
    @Override
    public SocialSyncResponse syncSocialAccount() {
        UserProvisioningService.Outcome outcome = provisionCurrentUser();

        if (outcome == UserProvisioningService.Outcome.SKIPPED_NO_EMAIL) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Your access token carries no email address, so no profile "
                            + "could be created. Add the email client scope to "
                            + "the frontend client and an email mapper to the "
                            + "identity provider in Keycloak."
            );
        }
        if (outcome == UserProvisioningService.Outcome.SKIPPED_UNRECOGNISED_SUBJECT) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "This token's subject is not a recognised user id"
            );
        }
        if (outcome == UserProvisioningService.Outcome.SKIPPED_PRIVILEGED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Company and admin accounts are provisioned by their own "
                            + "registration flow and cannot be synced here"
            );
        }

        return new SocialSyncResponse(
                outcome == UserProvisioningService.Outcome.CREATED,
                userProfileService.me()
        );
    }

    private UserProvisioningService.Outcome provisionCurrentUser() {
        try {
            return userProvisioningService.ensureProvisioned(
                    AuthUtils.extractJwtPrincipal()
            );
        } catch (DataIntegrityViolationException exception) {
            // Almost always the unique email: this identity's address already
            // belongs to a different account. Naming it beats the 404 the
            // caller would otherwise get from a profile that was never written.
            log.warn(
                    "Could not sync profile for {}",
                    AuthUtils.extractUserId(),
                    exception
            );
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email address already belongs to another account. "
                            + "Sign in with that account, or link it to this "
                            + "identity provider in Keycloak."
            );
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
