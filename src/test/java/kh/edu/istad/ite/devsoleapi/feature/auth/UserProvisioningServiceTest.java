package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProvisioningService userProvisioningService;

    @Test
    void createsProfileFromFederatedClaims() {
        when(userProfileRepository.existsById(USER_ID)).thenReturn(false);

        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of(
                                "email", "Sok.Dara@gmail.com",
                                "given_name", "Sok",
                                "family_name", "Dara",
                                "picture", "https://lh3.googleusercontent.com/a/photo"
                        ),
                        "ROLE_USER"
                ));

        assertEquals(UserProvisioningService.Outcome.CREATED, outcome);
        UserProfile saved = captureSavedProfile();
        assertEquals(USER_ID, saved.getId());
        assertEquals("sok.dara@gmail.com", saved.getEmail());
        assertEquals("Sok Dara", saved.getFullName());
        assertEquals(
                "https://lh3.googleusercontent.com/a/photo",
                saved.getAvatarUrl()
        );
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void leavesExistingProfileAlone() {
        when(userProfileRepository.existsById(USER_ID)).thenReturn(true);

        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of("email", "sok.dara@gmail.com"),
                        "ROLE_USER"
                ));

        assertEquals(UserProvisioningService.Outcome.ALREADY_PRESENT, outcome);
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void neverProvisionsCompanyAccounts() {
        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of("email", "contact@acme.com"),
                        "ROLE_COMPANY"
                ));

        assertEquals(UserProvisioningService.Outcome.SKIPPED_PRIVILEGED, outcome);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void neverProvisionsAdminAccounts() {
        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of("email", "admin@devsolve.com"),
                        "ROLE_ADMIN"
                ));

        assertEquals(UserProvisioningService.Outcome.SKIPPED_PRIVILEGED, outcome);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void skipsProvisioningWhenTokenCarriesNoEmail() {
        when(userProfileRepository.existsById(USER_ID)).thenReturn(false);

        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of("preferred_username", "dara"),
                        "ROLE_USER"
                ));

        assertEquals(UserProvisioningService.Outcome.SKIPPED_NO_EMAIL, outcome);
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void skipsProvisioningWhenSubjectIsNotAUserId() {
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("service-account-devsolve")
                        .claim("email", "svc@devsolve.com")
                        .build(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication);

        assertEquals(
                UserProvisioningService.Outcome.SKIPPED_UNRECOGNISED_SUBJECT,
                outcome
        );
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void fallsBackToEmailLocalPartWhenNoNameClaimIsPresent() {
        when(userProfileRepository.existsById(USER_ID)).thenReturn(false);

        UserProvisioningService.Outcome outcome =
                userProvisioningService.ensureProvisioned(authentication(
                        Map.of("email", "dara@gmail.com"),
                        "ROLE_USER"
                ));

        assertEquals(UserProvisioningService.Outcome.CREATED, outcome);
        UserProfile saved = captureSavedProfile();
        assertEquals("dara", saved.getFullName());
        assertNull(saved.getAvatarUrl());
    }

    private UserProfile captureSavedProfile() {
        ArgumentCaptor<UserProfile> captor =
                ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private JwtAuthenticationToken authentication(
            Map<String, Object> claims,
            String authority
    ) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID.toString());
        claims.forEach(builder::claim);

        Collection<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority(authority));
        return new JwtAuthenticationToken(builder.build(), authorities);
    }
}
