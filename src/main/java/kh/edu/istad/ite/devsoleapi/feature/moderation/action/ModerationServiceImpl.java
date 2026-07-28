package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor

public class ModerationServiceImpl implements ModerationService{
    private final ModerationActionRepository moderationActionRepository;
    private final UserProfileRepository userProfileRepository;
    private final ModerationActionMapper moderationActionMapper;

    private final Keycloak keycloak;
    private final KeycloakAdminProps keycloakAdminProps;


    @Override
    public ModerationActionResponse createModerationAction(
            UUID targetUserId,
            CreateModerationActionRequest request
    ) {
        // Only ADMIN can perform moderation actions
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can perform moderation actions"
            );
        }

        UUID adminId = extractCurrentUserId();

        // Admin cannot moderate their own account
        if (adminId.equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot moderate your own account"
            );
        }

        UserProfile admin = userProfileRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found"
                ));

        UserProfile targetUser = userProfileRepository
                .findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Target user profile not found"
                ));

        // Admin cannot moderate another ADMIN
        if (targetUserHasAdminRole(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An ADMIN cannot moderate another ADMIN"
            );
        }

        validateRequest(request);

        applyUserAction(targetUser, request);

        /*
         * WARN does not change the status.
         * SUSPEND, REMOVE and BAN change the status.
         */
        if (request.action() != ModerationActionType.WARN) {
            userProfileRepository.save(targetUser);
        }

        ModerationAction moderationAction =
                moderationActionMapper
                        .mapCreateModerationActionRequestToModerationAction(
                                request
                        );

        moderationAction.setAdmin(admin);
        moderationAction.setTargetType(ModerationTargetType.USER);
        moderationAction.setTargetId(targetUserId);

        ModerationAction savedAction =
                moderationActionRepository.save(moderationAction);

        return moderationActionMapper
                .mapModerationActionToModerationActionResponse(
                        savedAction
                );
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(
                    AuthUtils.extractUserId()
            );

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private boolean targetUserHasAdminRole(
            UUID targetUserId
    ) {
        try {
            return keycloak
                    .realm(keycloakAdminProps.getTargetRealm())
                    .users()
                    .get(targetUserId.toString())
                    .roles()
                    .realmLevel()
                    .listAll()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .anyMatch(roleName ->
                            roleName.equalsIgnoreCase("ADMIN")
                    );

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to verify target user's Keycloak roles",
                    exception
            );
        }
    }

    private void validateRequest(
            CreateModerationActionRequest request
    ) {

        if (request.action() == ModerationActionType.SUSPEND
                && request.expiresAt() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expiration date is required for suspension"
            );
        }

        if (request.action() != ModerationActionType.SUSPEND
                && request.expiresAt() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expiration date is only allowed for suspension"
            );
        }
    }

    private void applyUserAction(
            UserProfile targetUser,
            CreateModerationActionRequest request
    ) {

        switch (request.action()) {

            case WARN -> {
                // Only save warning history in moderation_actions.
            }

            case SUSPEND -> suspendUser(targetUser);

            case REMOVE, BAN -> removeUser(targetUser);
        }
    }

    private void suspendUser(
            UserProfile targetUser
    ) {

        if (targetUser.getStatus() == UserStatus.REMOVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A removed user cannot be suspended"
            );
        }

        if (targetUser.getStatus() == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User account is already suspended"
            );
        }

        targetUser.setStatus(UserStatus.SUSPENDED);
    }

    private void removeUser(
            UserProfile targetUser
    ) {

        if (targetUser.getStatus() == UserStatus.REMOVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User account has already been removed"
            );
        }

        targetUser.setStatus(UserStatus.REMOVED);
    }
}

