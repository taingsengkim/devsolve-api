package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.ws.rs.core.Response;
import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakCompanyIdentityService
        implements CompanyIdentityService {

    private static final String COMPANY_ROLE = "COMPANY";

    private final Keycloak keycloak;
    private final KeycloakAdminProps keycloakAdminProps;

    @Override
    public RegisteredCompany register(
            String email,
            String fullName,
            String password
    ) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String[] names = fullName.trim().split("\\s+", 2);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(normalizedEmail);
        user.setEmail(normalizedEmail);
        user.setFirstName(names[0]);
        user.setLastName(names.length > 1 ? names[1] : "");
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setRequiredActions(List.of("VERIFY_EMAIL"));
        user.setCredentials(List.of(passwordCredential(password)));

        UsersResource users = realmUsers();
        try (Response response = users.create(user)) {
            if (response.getStatus() == HttpStatus.CONFLICT.value()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Business email is already registered"
                );
            }
            if (response.getStatus() != HttpStatus.CREATED.value()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Identity provider could not create the company account"
                );
            }

            UUID userId = UUID.fromString(
                    CreatedResponseUtil.getCreatedId(response)
            );
            RegisteredCompany company = new RegisteredCompany(
                    userId,
                    normalizedEmail,
                    fullName.trim()
            );

            UserResource userResource;
            try {
                userResource = users.get(userId.toString());
                assignCompanyRole(userResource);
            } catch (RuntimeException exception) {
                delete(company);
                throw exception;
            }

            // Not fatal. The account exists and can sign in, and Keycloak
            // answers 500 here whenever the realm has no SMTP server — which
            // used to delete a perfectly good account and fail the whole
            // registration. The caller re-sends from
            // /organizations/me/verification-email.
            try {
                userResource.sendVerifyEmail();
            } catch (RuntimeException exception) {
                log.error(
                        "Could not send the verification email for {}",
                        normalizedEmail,
                        exception
                );
            }
            return company;
        }
    }

    @Override
    public boolean isEmailVerified(UUID companyUserId) {
        Boolean verified = realmUsers()
                .get(companyUserId.toString())
                .toRepresentation()
                .isEmailVerified();
        return Boolean.TRUE.equals(verified);
    }

    @Override
    public void sendVerificationEmail(UUID companyUserId) {
        UserResource userResource = realmUsers().get(companyUserId.toString());
        if (!Boolean.TRUE.equals(
                userResource.toRepresentation().isEmailVerified()
        )) {
            userResource.sendVerifyEmail();
        }
    }

    @Override
    public Set<UUID> findUserIdsByRealmRole(String role) {
        return keycloak.realm(keycloakAdminProps.getTargetRealm())
                .roles()
                .get(role)
                .getUserMembers()
                .stream()
                .map(UserRepresentation::getId)
                .filter(Objects::nonNull)
                .map(this::parseUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void delete(RegisteredCompany company) {
        try {
            realmUsers().get(company.id().toString()).remove();
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to remove Keycloak company account {}",
                    company.id(),
                    exception
            );
        }
    }

    private UsersResource realmUsers() {
        return keycloak.realm(keycloakAdminProps.getTargetRealm()).users();
    }

    private void assignCompanyRole(UserResource userResource) {
        RoleRepresentation companyRole = keycloak
                .realm(keycloakAdminProps.getTargetRealm())
                .roles()
                .get(COMPANY_ROLE)
                .toRepresentation();
        userResource.roles().realmLevel().add(List.of(companyRole));
    }

    private CredentialRepresentation passwordCredential(String password) {
        CredentialRepresentation credential =
                new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring Keycloak user with non-UUID id: {}", value);
            return null;
        }
    }
}
