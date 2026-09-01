package kh.edu.istad.ite.devsoleapi.feature.userprofile.service.impl;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.SocialPlatform;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserSocialLink;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.AdminUserSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.PublicUserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.SocialLinkRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.mapper.UserProfileMapper;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.validation.SocialLinkValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository
            reportRewardRepository;
    @Mock
    private Keycloak keycloak;
    @Mock
    private KeycloakAdminProps keycloakAdminProps;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private RealmResource realmResource;
    @Mock
    private UsersResource usersResource;
    @Mock
    private UserResource userResource;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private OrganizationAuthorizationService organizationAuthorization;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminListsLocalProfilesWithSearchStatusAndPagination() {
        UUID adminId = UUID.randomUUID();
        UserProfile profile = profile();
        authenticate(adminId, "ADMIN");
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(userProfileRepository.findForAdmin(
                eq("%sokha chan%"),
                eq(EnumSet.of(UserStatus.ACTIVE)),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(profile)));

        Page<AdminUserSummaryResponse> result = service().getAllForAdmin(
                "  Sokha Chan  ",
                UserStatus.ACTIVE,
                1,
                25
        );

        AdminUserSummaryResponse response = result.getContent().getFirst();
        assertEquals(profile.getId(), response.id());
        assertEquals(profile.getEmail(), response.email());
        assertEquals(profile.getCriticalReports(), response.criticalReports());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(25, pageableCaptor.getValue().getPageSize());
        assertEquals(
                org.springframework.data.domain.Sort.Direction.DESC,
                pageableCaptor.getValue().getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    /**
     * Searching without a status filter is the common case from the admin
     * table, and it used to reach the repository with a null status. Postgres
     * cannot type an untyped named-enum parameter that only appears in an
     * "is null" test, so the query failed to parse and the endpoint answered
     * 500. "Any status" now travels as every status.
     */
    @Test
    void adminSearchWithoutStatusAsksForEveryStatusRatherThanANullOne() {
        authenticate(UUID.randomUUID(), "ADMIN");
        when(userProfileRepository.findForAdmin(
                eq("%kim%"),
                eq(EnumSet.allOf(UserStatus.class)),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<AdminUserSummaryResponse> result = service().getAllForAdmin(
                "kim",
                null,
                0,
                20
        );

        assertEquals(0, result.getTotalElements());
        verify(userProfileRepository).findForAdmin(
                eq("%kim%"),
                eq(EnumSet.allOf(UserStatus.class)),
                any(Pageable.class)
        );
    }

    @Test
    void adminWithoutSearchAndStatusListsAllProfiles() {
        authenticate(UUID.randomUUID(), "ADMIN");
        when(userProfileRepository.findAll(
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<AdminUserSummaryResponse> result = service().getAllForAdmin(
                null,
                null,
                0,
                20
        );

        assertEquals(0, result.getTotalElements());
        verify(userProfileRepository).findAll(
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
        verify(userProfileRepository, never()).findForAdmin(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void adminWithoutSearchFiltersByStatusWithoutUsingLikeQuery() {
        authenticate(UUID.randomUUID(), "ADMIN");
        when(userProfileRepository.findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<AdminUserSummaryResponse> result = service().getAllForAdmin(
                "   ",
                UserStatus.ACTIVE,
                0,
                20
        );

        assertEquals(0, result.getTotalElements());
        verify(userProfileRepository).findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
        verify(userProfileRepository, never()).findForAdmin(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void nonAdminCannotListUsersEvenIfServiceIsCalledDirectly() {
        authenticate(UUID.randomUUID(), "USER");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getAllForAdmin(null, null, 0, 20)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(userProfileRepository, never()).findForAdmin(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void publicDirectoryNeedsNoAuthenticationAndOnlyQueriesActiveUsers() {
        UserProfile profile = profile();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(userProfileRepository.findPublicProfiles(
                eq("%cambodia%"),
                eq(UserStatus.ACTIVE),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(profile)));

        Page<PublicUserProfileResponse> result = service()
                .getPublicProfiles("  Cambodia ", 0, 20);

        PublicUserProfileResponse response =
                result.getContent().getFirst();
        assertEquals(profile.getId(), response.id());
        assertEquals(profile.getFullName(), response.fullName());
        assertEquals(profile.getReputation(), response.reputation());
        assertEquals(
                org.springframework.data.domain.Sort.Direction.DESC,
                pageableCaptor.getValue().getSort()
                        .getOrderFor("reputation")
                        .getDirection()
        );
    }

    @Test
    void publicDirectoryWithoutSearchDoesNotUseLikeQuery() {
        when(userProfileRepository.findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<PublicUserProfileResponse> result = service()
                .getPublicProfiles(null, 0, 20);

        assertEquals(0, result.getTotalElements());
        verify(userProfileRepository).findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
        verify(userProfileRepository, never()).findPublicProfiles(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void publicDirectoryWithBlankSearchDoesNotUseLikeQuery() {
        when(userProfileRepository.findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(Page.empty());

        Page<PublicUserProfileResponse> result = service()
                .getPublicProfiles("   ", 0, 20);

        assertEquals(0, result.getTotalElements());
        verify(userProfileRepository).findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
        verify(userProfileRepository, never()).findPublicProfiles(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void publicDetailOnlyReturnsActiveProfile() {
        UserProfile profile = profile();
        when(userProfileRepository.findByIdAndStatus(
                profile.getId(),
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile));

        PublicUserProfileResponse response = service()
                .getPublicProfile(profile.getId());

        assertEquals(profile.getId(), response.id());
        assertEquals(profile.getCountry(), response.country());
    }

    /**
     * The roster already shows a colleague this address, so a profile page
     * hiding it from the same viewer was inconsistent rather than protective.
     */
    @Test
    void aColleagueSeesTheEmailOnAProfile() {
        UserProfile profile = profile();
        UUID viewerId = UUID.randomUUID();
        authenticate(viewerId, "MEMBER");
        when(userProfileRepository.findByIdAndStatus(
                profile.getId(),
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile));
        when(organizationAuthorization.shareOrganization(
                viewerId,
                profile.getId()
        )).thenReturn(true);

        assertEquals(
                profile.getEmail(),
                service().getPublicProfile(profile.getId()).email()
        );
    }

    @Test
    void anAnonymousOrUnrelatedReaderDoesNotSeeTheEmail() {
        UserProfile profile = profile();
        when(userProfileRepository.findByIdAndStatus(
                profile.getId(),
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile));

        assertNull(service().getPublicProfile(profile.getId()).email());
        verify(organizationAuthorization, never())
                .shareOrganization(any(), any());
    }

    /**
     * A directory would otherwise pay two organization lookups per row for a
     * column it does not show.
     */
    @Test
    void theDirectoryNeverPaysForTheEmailCheck() {
        when(userProfileRepository.findAllByStatus(
                eq(UserStatus.ACTIVE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(profile())));

        Page<PublicUserProfileResponse> result = service()
                .getPublicProfiles(null, 0, 20);

        assertNull(result.getContent().getFirst().email());
        verify(organizationAuthorization, never())
                .shareOrganization(any(), any());
    }

    @Test
    void unavailableOrSuspendedProfileIsNotPubliclyDiscoverable() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByIdAndStatus(
                userId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getPublicProfile(userId)
        );
    }

    @Test
    void updateMeReplacesSocialLinksWhenCollectionIsProvided() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.getSocialLinks().add(UserSocialLink.builder()
                .user(profile)
                .platform(SocialPlatform.FACEBOOK)
                .url("https://facebook.com/old-profile")
                .build());
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        service().updateMe(new UpdateUserProfileRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new SocialLinkRequest(
                                SocialPlatform.GITHUB,
                                "https://github.com/sokha-chan"
                        ),
                        new SocialLinkRequest(
                                SocialPlatform.WEBSITE,
                                "https://sokha.dev"
                        )
                )
        ));

        assertEquals(2, profile.getSocialLinks().size());
        assertEquals(
                List.of(SocialPlatform.GITHUB, SocialPlatform.WEBSITE),
                profile.getSocialLinks().stream()
                        .map(UserSocialLink::getPlatform)
                        .sorted()
                        .toList()
        );
    }

    @Test
    void uploadAvatarStoresUnderTheCallerOwnPrefixAndSavesTheUrl() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.setAvatarUrl("https://cdn.example.com/bucket/public/old.png");
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );
        when(imageStorageService.replace(
                eq("user-profiles/" + userId),
                eq("https://cdn.example.com/bucket/public/old.png"),
                eq(file)
        )).thenReturn("https://cdn.example.com/bucket/public/new.png");

        service().uploadAvatar(file);

        assertEquals(
                "https://cdn.example.com/bucket/public/new.png",
                profile.getAvatarUrl()
        );
        verify(userProfileRepository).saveAndFlush(profile);
    }

    @Test
    void uploadAvatarCannotTargetAnotherUsersProfile() {
        UUID callerId = UUID.randomUUID();
        authenticate(callerId, "USER");
        when(userProfileRepository.findById(callerId))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        // The identity comes from the token, never from a path variable, so
        // the only profile reachable here is the caller's own.
        assertThrows(
                ResponseStatusException.class,
                () -> service().uploadAvatar(file)
        );
        verify(imageStorageService, never())
                .replace(anyString(), anyString(), any());
    }

    @Test
    void removeAvatarClearsTheUrlAndDropsTheStoredObject() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.setAvatarUrl("https://cdn.example.com/bucket/public/old.png");
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        service().removeAvatar();

        assertNull(profile.getAvatarUrl());
        verify(imageStorageService)
                .remove("https://cdn.example.com/bucket/public/old.png");
        verify(userProfileRepository).saveAndFlush(profile);
    }

    @Test
    void uploadCoverImageStoresUnderItsOwnPrefixAndSavesTheUrl() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.setCoverImageUrl(
                "https://cdn.example.com/bucket/public/old-cover.png"
        );
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );
        when(imageStorageService.replace(
                eq("user-profiles/" + userId + "/cover"),
                eq("https://cdn.example.com/bucket/public/old-cover.png"),
                eq(file)
        )).thenReturn("https://cdn.example.com/bucket/public/new-cover.png");

        service().uploadCoverImage(file);

        assertEquals(
                "https://cdn.example.com/bucket/public/new-cover.png",
                profile.getCoverImageUrl()
        );
        verify(userProfileRepository).saveAndFlush(profile);
    }

    @Test
    void uploadCoverImageLeavesTheAvatarAlone() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.setAvatarUrl("https://cdn.example.com/bucket/public/face.png");
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );
        when(imageStorageService.replace(anyString(), any(), any()))
                .thenReturn("https://cdn.example.com/bucket/public/cover.png");

        service().uploadCoverImage(file);

        // The two images are independent; setting one must never be handed the
        // other's URL as the object to drop.
        assertEquals(
                "https://cdn.example.com/bucket/public/face.png",
                profile.getAvatarUrl()
        );
        verify(imageStorageService, never())
                .remove("https://cdn.example.com/bucket/public/face.png");
    }

    @Test
    void uploadCoverImageCannotTargetAnotherUsersProfile() {
        UUID callerId = UUID.randomUUID();
        authenticate(callerId, "USER");
        when(userProfileRepository.findById(callerId))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service().uploadCoverImage(file)
        );
        verify(imageStorageService, never())
                .replace(anyString(), anyString(), any());
    }

    @Test
    void removeCoverImageClearsTheUrlAndDropsTheStoredObject() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = profile();
        profile.setId(userId);
        profile.setCoverImageUrl(
                "https://cdn.example.com/bucket/public/old-cover.png"
        );
        authenticate(userId, "USER");
        stubProfile(userId, profile);

        service().removeCoverImage();

        assertNull(profile.getCoverImageUrl());
        verify(imageStorageService)
                .remove("https://cdn.example.com/bucket/public/old-cover.png");
        verify(userProfileRepository).saveAndFlush(profile);
    }

    /**
     * Reads answer from the local row and the caller's token, so no Keycloak
     * admin stubbing belongs here: only a first or last name change goes to
     * Keycloak now.
     */
    private void stubProfile(UUID userId, UserProfile profile) {
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
    }

    private UserProfileServiceImpl service() {
        return new UserProfileServiceImpl(
                keycloak,
                keycloakAdminProps,
                userProfileRepository,
                userProfileMapper,
                new SocialLinkValidator(),
                imageStorageService,
                organizationAuthorization,
                reportRewardRepository
        );
    }

    private UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setFullName("Sokha Chan");
        profile.setEmail("sokha.chan@example.com");
        profile.setStatus(UserStatus.ACTIVE);
        profile.setCountry("Cambodia");
        profile.setReputation(1250);
        profile.setTotalReports(20);
        profile.setValidReports(12);
        profile.setCriticalReports(3);
        profile.setRecognitionCount(4);
        profile.setCreatedAt(LocalDateTime.now().minusMonths(2));
        return profile;
    }

    private void authenticate(UUID userId, String role) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + role
                        ))
                )
        );
    }
}
