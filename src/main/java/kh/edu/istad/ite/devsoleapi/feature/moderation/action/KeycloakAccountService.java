package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Mirrors a moderation decision into Keycloak, so a blocked account stops
 * being issued new tokens rather than merely being refused at the API.
 *
 * <p>Failures are logged, not thrown. {@code AccountStatusFilter} already
 * enforces the decision from the database on every request, so Keycloak here is
 * defence in depth: if it is unreachable the ban still holds, and the operation
 * can be retried without leaving the database inconsistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAccountService {

    private final Keycloak keycloak;
    private final KeycloakAdminProps keycloakAdminProps;

    /**
     * Disables the account and terminates its live sessions, so refresh tokens
     * cannot be exchanged for fresh access tokens.
     */
    public void disable(UUID userId) {
        withUser(userId, "disable", userResource -> {
            UserRepresentation representation =
                    userResource.toRepresentation();
            representation.setEnabled(false);
            userResource.update(representation);
            userResource.logout();
        });
    }

    public void enable(UUID userId) {
        withUser(userId, "enable", userResource -> {
            UserRepresentation representation =
                    userResource.toRepresentation();
            representation.setEnabled(true);
            userResource.update(representation);
        });
    }

    private void withUser(
            UUID userId,
            String operation,
            java.util.function.Consumer<UserResource> action
    ) {
        try {
            action.accept(
                    keycloak.realm(keycloakAdminProps.getTargetRealm())
                            .users()
                            .get(userId.toString())
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Could not {} Keycloak account {}; the API still enforces "
                            + "the stored account status, but the identity "
                            + "provider is now out of step",
                    operation,
                    userId,
                    exception
            );
        }
    }
}
