package kh.edu.istad.ite.devsoleapi.feature.userprofile.service.impl;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

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
                eq("sokha chan"),
                eq(UserStatus.ACTIVE),
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
                eq("cambodia"),
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
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(profile));
        when(keycloakAdminProps.getTargetRealm()).thenReturn("devsolve");
        when(keycloak.realm("devsolve")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(userId.toString()))
                .thenReturn(userResource);
        when(userResource.toRepresentation())
                .thenReturn(new UserRepresentation());

        service().updateMe(new UpdateUserProfileRequest(
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

    private UserProfileServiceImpl service() {
        return new UserProfileServiceImpl(
                keycloak,
                keycloakAdminProps,
                userProfileRepository,
                userProfileMapper,
                new SocialLinkValidator()
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
