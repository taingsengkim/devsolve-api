package kh.edu.istad.ite.devsoleapi.feature.userprofile.service.impl;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.SocialPlatform;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserSocialLink;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UsernamePolicy;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.AdminUserSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.PublicUserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.SocialLinkRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UsernameAvailabilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.mapper.UserProfileMapper;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserProfileService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.validation.SocialLinkValidator;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
    private final ImageStorageService imageStorageService;
    private final OrganizationAuthorizationService organizationAuthorization;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse me() {
        UUID userId = extractCurrentUserId();
        return toResponse(findUserProfile(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateMe(UpdateUserProfileRequest request) {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);

        userProfileMapper.mapUpdateUserProfileRequestToUserProfile(request, userProfile);
        if (request.username() != null) {
            applyUsernameChange(userProfile, request.username());
        }
        if (request.socialLinks() != null) {
            replaceSocialLinks(userProfile, request.socialLinks());
        }

        // Keycloak owns first and last name, so only a change to those needs
        // the admin API. Reaching for it on every edit put a remote call that
        // can fail in the path of changes that never touch it.
        if (request.firstName() == null && request.lastName() == null) {
            return toResponse(userProfile);
        }

        UserResource keycloakUserResource = findKeycloakUser(userId);
        UserRepresentation keycloakUser = keycloakUserResource.toRepresentation();

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

        // Answers from what was just written rather than from the token, which
        // still carries the name the caller has only now replaced.
        keycloakUser.setEmail(userProfile.getEmail());
        return userProfileMapper.toUserProfileResponse(keycloakUser, userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse uploadAvatar(MultipartFile file) {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);

        String avatarUrl = imageStorageService.replace(
                "user-profiles/" + userId,
                userProfile.getAvatarUrl(),
                file
        );
        userProfile.setAvatarUrl(avatarUrl);
        userProfileRepository.saveAndFlush(userProfile);

        return toResponse(userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse removeAvatar() {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);

        imageStorageService.remove(userProfile.getAvatarUrl());
        userProfile.setAvatarUrl(null);
        userProfileRepository.saveAndFlush(userProfile);

        return toResponse(userProfile);
    }

    /**
     * Under its own key prefix so a bucket listing tells covers and avatars
     * apart. Sharing one would work — {@code replace} drops only the URL it is
     * handed — but leaves the two indistinguishable outside the database.
     */
    @Override
    @Transactional
    public UserProfileResponse uploadCoverImage(MultipartFile file) {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);

        String coverImageUrl = imageStorageService.replace(
                coverImagePrefix(userId),
                userProfile.getCoverImageUrl(),
                file
        );
        userProfile.setCoverImageUrl(coverImageUrl);
        userProfileRepository.saveAndFlush(userProfile);

        return toResponse(userProfile);
    }

    @Override
    @Transactional
    public UserProfileResponse removeCoverImage() {
        UUID userId = extractCurrentUserId();
        UserProfile userProfile = findUserProfile(userId);

        imageStorageService.remove(userProfile.getCoverImageUrl());
        userProfile.setCoverImageUrl(null);
        userProfileRepository.saveAndFlush(userProfile);

        return toResponse(userProfile);
    }

    private String coverImagePrefix(UUID userId) {
        return "user-profiles/" + userId + "/cover";
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
        PageRequest pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<UserProfile> profiles;
        if (normalizedQuery == null) {
            profiles = status == null
                    ? userProfileRepository.findAll(pageable)
                    : userProfileRepository.findAllByStatus(
                            status,
                            pageable
                    );
        } else {
            profiles = userProfileRepository.findForAdmin(
                    containsPattern(normalizedQuery),
                    status,
                    pageable
            );
        }
        return profiles.map(this::toAdminSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicUserProfileResponse> getPublicProfiles(
            String query,
            int pageNumber,
            int pageSize
    ) {
        String normalizedQuery = normalizeQuery(query);
        PageRequest pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "reputation")
                        .and(Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        ))
        );
        Page<UserProfile> profiles = normalizedQuery == null
                ? userProfileRepository.findAllByStatus(
                        UserStatus.ACTIVE,
                        pageable
                )
                : userProfileRepository.findPublicProfiles(
                        containsPattern(normalizedQuery),
                        UserStatus.ACTIVE,
                        pageable
                );
        return profiles.map(this::toPublicProfile);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfile(UUID userId) {
        return userProfileRepository.findByIdAndStatus(
                        userId,
                        UserStatus.ACTIVE
                )
                .map(this::toProfileForViewer)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Public user profile not found"
                ));
    }

    private UserProfileResponse toResponse(UserProfile userProfile) {
        return userProfileMapper.toUserProfileResponse(
                currentIdentity(userProfile),
                userProfile
        );
    }

    /**
     * The caller's name and email, taken from the access token they are already
     * holding.
     *
     * <p>This used to be a Keycloak Admin API lookup on every profile read. It
     * was the only step on that path that could fail for a perfectly valid
     * user: the admin client throws raw JAX-RS exceptions that nothing
     * translates, so a realm the service account cannot read users in, or an
     * account it cannot see, turned a profile that exists into a 500. The token
     * is already verified and carries the same fields, so reading it is both
     * cheaper and one less thing to go wrong.
     */
    private UserRepresentation currentIdentity(UserProfile userProfile) {
        Jwt token = AuthUtils.extractJwtPrincipal().getToken();
        String firstName = token.getClaimAsString("given_name");
        String lastName = token.getClaimAsString("family_name");

        // A client that does not request the profile scope gets neither claim.
        // The stored full name is the only other place these two live.
        if (firstName == null && lastName == null) {
            String fullName = userProfile.getFullName();
            int separator = fullName == null ? -1 : fullName.indexOf(' ');
            if (separator > 0) {
                firstName = fullName.substring(0, separator);
                lastName = fullName.substring(separator + 1).trim();
            } else {
                firstName = fullName;
            }
        }

        UserRepresentation identity = new UserRepresentation();
        identity.setFirstName(firstName);
        identity.setLastName(lastName);
        // The local row owns the email: it is non-null there, and moderation
        // and ownership checks already read it from the database.
        identity.setEmail(userProfile.getEmail());
        return identity;
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

    private String containsPattern(String normalizedQuery) {
        return "%" + normalizedQuery + "%";
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

    /**
     * Applies a requested handle, or explains why it cannot have it.
     *
     * <p>Asking for the handle already held is a no-op rather than an error:
     * a client that PATCHes the whole form back would otherwise start the
     * cooldown, or trip over it, without anything having changed.
     */
    private void applyUsernameChange(
            UserProfile profile,
            String requested
    ) {
        String username = requested.trim();
        if (username.equalsIgnoreCase(profile.getUsername())) {
            return;
        }

        LocalDateTime changeableAt =
                userProfileMapper.usernameChangeableAt(profile);
        if (changeableAt != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username was changed recently and can be changed again "
                            + "after " + changeableAt
            );
        }

        UsernameAvailabilityResponse availability = checkUsername(username);
        if (!availability.available()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    availability.reason()
            );
        }

        profile.setUsername(username);
        profile.setUsernameChangedAt(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getPublicProfileByUsername(
            String username
    ) {
        return userProfileRepository.findByUsernameIgnoreCaseAndStatus(
                        username == null ? "" : username.trim(),
                        UserStatus.ACTIVE
                )
                .map(this::toProfileForViewer)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Public user profile not found"
                ));
    }

    /**
     * The profile, with the email filled in only for somebody entitled to it.
     *
     * <p>These endpoints are open to anonymous callers, so the common case has
     * no viewer at all and the address is simply left out.
     */
    private PublicUserProfileResponse toProfileForViewer(UserProfile profile) {
        UUID viewerId = currentUserIdOrNull();
        boolean visible = viewerId != null
                && (viewerId.equals(profile.getId())
                || organizationAuthorization.shareOrganization(
                        viewerId,
                        profile.getId()
                ));

        return toPublicProfile(profile, visible ? profile.getEmail() : null);
    }

    private UUID currentUserIdOrNull() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication instanceof JwtAuthenticationToken token)
                || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return UUID.fromString(token.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UsernameAvailabilityResponse checkUsernameAvailability(
            String username
    ) {
        return checkUsername(username == null ? "" : username.trim());
    }

    /**
     * The four ways a handle can be refused, each answered in the words the
     * person typing it needs. Collapsing them into one "unavailable" is what
     * makes a user try the same reserved name three times.
     */
    private UsernameAvailabilityResponse checkUsername(String username) {
        if (!UsernamePolicy.isValid(username)) {
            return UsernameAvailabilityResponse.unavailable(
                    username,
                    "Username must be "
                            + UsernamePolicy.MIN_LENGTH + " to "
                            + UsernamePolicy.MAX_LENGTH + " characters, start "
                            + "and end with a letter or number, and may "
                            + "contain dots, underscores and hyphens between"
            );
        }
        if (UsernamePolicy.isReserved(username)) {
            return UsernameAvailabilityResponse.unavailable(
                    username,
                    "That username is reserved"
            );
        }
        if (userProfileRepository.existsByUsernameIgnoreCase(username)) {
            return UsernameAvailabilityResponse.unavailable(
                    username,
                    "That username is already taken"
            );
        }
        return UsernameAvailabilityResponse.available(username);
    }

    /**
     * A directory row, where the email is always withheld.
     *
     * <p>Establishing that a viewer is a colleague costs two organization
     * lookups, which on a page of profiles is a query per row for a column no
     * directory shows. The single-profile reads pay it; a listing does not.
     */
    private PublicUserProfileResponse toPublicProfile(
            UserProfile profile
    ) {
        return toPublicProfile(profile, null);
    }

    private PublicUserProfileResponse toPublicProfile(
            UserProfile profile,
            String email
    ) {
        return new PublicUserProfileResponse(
                profile.getId(),
                profile.getUsername(),
                profile.getFullName(),
                email,
                profile.getBiography(),
                profile.getAvatarUrl(),
                profile.getCoverImageUrl(),
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


    @Override
    public Integer getReputation(UUID userId) {

        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User profile not found")
                );

        return userProfile.getReputation();
    }

}
