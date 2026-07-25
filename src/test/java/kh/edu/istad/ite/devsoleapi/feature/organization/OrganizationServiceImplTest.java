package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private OrganizationUserProfileRepository userProfileRepository;

    @Mock
    private CompanyIdentityService companyIdentityService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationCreatesCompanyIdentityProfileAndOrganization() {
        UUID userId = UUID.randomUUID();
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = validRequest();

        when(userProfileRepository.findByEmailIgnoreCase("owner@acme.com"))
                .thenReturn(Optional.empty());
        when(organizationRepository.existsBySlug("acme-security"))
                .thenReturn(false);
        when(companyIdentityService.register(
                "owner@acme.com",
                "Acme Owner",
                "Password123!"
        )).thenReturn(new RegisteredCompany(
                userId,
                "owner@acme.com",
                "Acme Owner"
        ));
        when(userProfileRepository.saveAndFlush(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(organizationRepository.saveAndFlush(any(Organization.class)))
                .thenAnswer(invocation -> {
                    Organization organization = invocation.getArgument(0);
                    organization.setId(UUID.randomUUID());
                    return organization;
                });

        OrganizationResponse response = service.register(request);

        ArgumentCaptor<UserProfile> profileCaptor =
                ArgumentCaptor.forClass(UserProfile.class);
        ArgumentCaptor<Organization> organizationCaptor =
                ArgumentCaptor.forClass(Organization.class);
        verify(userProfileRepository).saveAndFlush(profileCaptor.capture());
        verify(organizationRepository)
                .saveAndFlush(organizationCaptor.capture());

        assertEquals(userId, profileCaptor.getValue().getId());
        assertEquals("owner@acme.com", profileCaptor.getValue().getEmail());
        assertEquals("Acme Owner", profileCaptor.getValue().getFullName());
        assertEquals(
                "Security Manager",
                organizationCaptor.getValue().getOwnerJobTitle()
        );
        assertEquals(
                "We want to run a responsible disclosure program.",
                organizationCaptor.getValue().getJoiningReason()
        );
        assertEquals(userId, response.ownerId());
        assertEquals("Acme Security", response.name());
        assertEquals("acme-security", response.slug());
        assertEquals("https://www.acme.com", response.websiteUrl());
        assertEquals("acme.com", response.domain());
        assertEquals("11-50", response.companySize());
        assertEquals("Cambodia", response.country());
        assertEquals(OrganizationStatus.PENDING, response.status());
    }

    @Test
    void registrationRejectsPersonalEmailDomain() {
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = new OrganizationRequest(
                "Acme Owner",
                "Security Manager",
                "owner@gmail.com",
                "Password123!",
                "Password123!",
                "Acme Security",
                "https://acme.com",
                Industry.TECHNOLOGY,
                "11-50",
                "Cambodia",
                "We want to run a responsible disclosure program."
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.register(request)
        );

        verify(companyIdentityService, never()).register(
                any(),
                any(),
                any()
        );
        verify(userProfileRepository, never())
                .saveAndFlush(any(UserProfile.class));
        verify(organizationRepository, never())
                .saveAndFlush(any(Organization.class));
    }

    @Test
    void registrationRejectsMismatchedPasswords() {
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = validRequest();
        OrganizationRequest mismatchedRequest = new OrganizationRequest(
                request.fullName(),
                request.jobTitle(),
                request.email(),
                request.password(),
                "DifferentPassword123!",
                request.companyName(),
                request.companyWebsite(),
                request.industry(),
                request.companySize(),
                request.country(),
                request.joiningReason()
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.register(mismatchedRequest)
        );

        verify(companyIdentityService, never()).register(
                any(),
                any(),
                any()
        );
    }

    @Test
    void firstInvitationCreatesPendingMembership() {
        UUID ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID invitedUserId = UUID.randomUUID();
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);

        UserProfile owner = new UserProfile();
        owner.setId(ownerId);
        owner.setEmail("owner@acme.com");
        owner.setFullName("Acme Owner");

        UserProfile invitedUser = new UserProfile();
        invitedUser.setId(invitedUserId);
        invitedUser.setEmail("member@acme.com");
        invitedUser.setFullName("Acme Member");

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setOwner(owner);

        Jwt jwt = Jwt.withTokenValue("company-token")
                .header("alg", "none")
                .subject(ownerId.toString())
                .claim("email", owner.getEmail())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_COMPANY")
                        )
                )
        );

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(userProfileRepository.findByEmailIgnoreCase("member@acme.com"))
                .thenReturn(Optional.of(invitedUser));
        when(memberRepository.findByOrganizationIdAndUserId(
                organizationId,
                invitedUserId
        )).thenReturn(Optional.empty());
        when(memberRepository.saveAndFlush(any(OrganizationMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InvitationResponse response = service.inviteMember(
                new InviteMemberRequest("member@acme.com", OrgRole.MEMBER)
        );

        assertEquals(MembershipStatus.SUSPENDED, response.member().status());
        assertTrue(response.member().invitationPending());
        assertNotNull(response.invitationToken());
        assertNotNull(response.expiresAt());
    }

    private OrganizationServiceImpl createService(
            WebsiteUrlService websiteUrlService
    ) {
        return new OrganizationServiceImpl(
                organizationRepository,
                memberRepository,
                userProfileRepository,
                new OrganizationMapper(websiteUrlService),
                websiteUrlService,
                companyIdentityService
        );
    }

    private OrganizationRequest validRequest() {
        return new OrganizationRequest(
                "Acme Owner",
                "Security Manager",
                "owner@acme.com",
                "Password123!",
                "Password123!",
                "Acme Security",
                "www.acme.com",
                Industry.TECHNOLOGY,
                "11-50",
                "Cambodia",
                "We want to run a responsible disclosure program."
        );
    }
}
