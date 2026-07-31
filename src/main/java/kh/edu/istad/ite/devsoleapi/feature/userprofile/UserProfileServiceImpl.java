package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.AdminUserSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.PublicUserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.SocialLinkRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.Locale;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private static final String ADMIN_ROLE = "ADMIN";

    private final Keycloak keycloak;
    private final KeycloakAdminProps keycloakAdminProps;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;
    private final SocialLinkValidator socialLinkValidator;

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
        if (request.socialLinks() != null) {
            replaceSocialLinks(userProfile, request.socialLinks());
        }

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

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> getAllForAdmin(
            String query,
            UserStatus status,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin();
        String normalizedQuery = normalizeQuery(query);
        return userProfileRepository.findForAdmin(
                normalizedQuery,
                status,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        ).map(this::toAdminSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicUserProfileResponse> getPublicProfiles(
            String query,
            int pageNumber,
            int pageSize
    ) {
        return userProfileRepository.findPublicProfiles(
                normalizeQuery(query),
                UserStatus.ACTIVE,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "reputation")
                                .and(Sort.by(
                                        Sort.Direction.DESC,
                                        "createdAt"
                                ))
                )
        ).map(this::toPublicProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID userId) {
        return userProfileRepository.findByIdAndStatus(
                        userId,
                        UserStatus.ACTIVE
                )
                .map(this::toPublicProfile)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Public user profile not found"
                ));
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

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: ADMIN"
            );
        }
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    private AdminUserSummaryResponse toAdminSummary(
            UserProfile profile
    ) {
        return new AdminUserSummaryResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getEmail(),
                profile.getAvatarUrl(),
                profile.getCountry(),
                profile.getStatus(),
                profile.getReputation(),
                profile.getTotalReports(),
                profile.getValidReports(),
                profile.getCriticalReports(),
                profile.getRecognitionCount(),
                profile.getLastLoginAt(),
                profile.getCreatedAt()
        );
    }

    private PublicUserProfileResponse toPublicProfile(
            UserProfile profile
    ) {
        return new PublicUserProfileResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getBiography(),
                profile.getAvatarUrl(),
                profile.getCountry(),
                userProfileMapper.toSocialLinkResponses(profile),
                profile.getReputation(),
                profile.getTotalReports(),
                profile.getValidReports(),
                profile.getCriticalReports(),
                profile.getRecognitionCount(),
                profile.getCreatedAt()
        );
    }

    private void replaceSocialLinks(
            UserProfile profile,
            java.util.List<SocialLinkRequest> requests
    ) {
        Map<SocialPlatform, String> requestedLinks =
                new EnumMap<>(SocialPlatform.class);
        for (SocialLinkRequest request : requests) {
            String previous = requestedLinks.putIfAbsent(
                    request.platform(),
                    socialLinkValidator.normalize(
                            request.platform(),
                            request.url()
                    )
            );
            if (previous != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only one link is allowed for each social platform"
                );
            }
        }

        Map<SocialPlatform, UserSocialLink> existingLinks =
                new EnumMap<>(SocialPlatform.class);
        profile.getSocialLinks().forEach(link ->
                existingLinks.put(link.getPlatform(), link));
        profile.getSocialLinks().removeIf(link ->
                !requestedLinks.containsKey(link.getPlatform()));

        requestedLinks.forEach((platform, url) -> {
            UserSocialLink link = existingLinks.get(platform);
            if (link == null) {
                profile.getSocialLinks().add(UserSocialLink.builder()
                        .user(profile)
                        .platform(platform)
                        .url(url)
                        .build());
            } else {
                link.setUrl(url);
            }
        });
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
