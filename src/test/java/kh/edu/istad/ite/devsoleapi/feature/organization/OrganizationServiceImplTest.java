package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import kh.edu.istad.ite.devsoleapi.common.exception.MissingPermissionException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.RejectOrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationMembershipResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRoleResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.PendingInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberPermissionsRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationNextAction;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationReviewDecision;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UsernameAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private OrganizationReviewHistoryRepository reviewHistoryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramService programService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportRewardRepository reportRewardRepository;

    @Mock
    private UsernameAllocator usernameAllocator;

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
        // Typed with spaces, stored as the profile column accepts it.
        assertEquals("+85512345678", profileCaptor.getValue().getPhone());
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
        assertEquals(1, response.submissionVersion());
        verify(eventPublisher).publishEvent(any(OrganizationLifecycleEvent.class));
    }

    @Test
    void registrationRejectsPersonalEmailDomain() {
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = new OrganizationRequest(
                "Acme Owner",
                "Security Manager",
                "owner@gmail.com",
                "+85512345678",
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

    /**
     * The DTO pattern accepts any punctuation, so the digit count is the
     * service's to enforce — and it has to happen before an account is created
     * at the identity provider.
     */
    @Test
    void registrationRejectsAPhoneNumberWithTooFewDigits() {
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = requestWithPhone("(02) 39-91");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(companyIdentityService, never()).register(any(), any(), any());
    }

    @Test
    void registrationRejectsAPhoneNumberWithTooManyDigits() {
        WebsiteUrlService websiteUrlService = new WebsiteUrlServiceImpl();
        OrganizationServiceImpl service = createService(websiteUrlService);
        OrganizationRequest request = requestWithPhone("+1234567890123456");

        assertThrows(
                ResponseStatusException.class,
                () -> service.register(request)
        );

        verify(companyIdentityService, never()).register(any(), any(), any());
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
                request.phone(),
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
    void ownerCanUploadOrganizationLogo() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        organization.setLogoUrl("https://cdn.example.com/old-logo.png");
        MockMultipartFile logo = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        String uploadedUrl = "https://cdn.example.com/new-logo.png";
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(imageStorageService.replace(
                "organizations/" + organization.getId(),
                organization.getLogoUrl(),
                logo
        )).thenReturn(uploadedUrl);
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).uploadLogo(logo);

        assertEquals(uploadedUrl, response.logoUrl());
        assertEquals(uploadedUrl, organization.getLogoUrl());
        verify(organizationRepository).saveAndFlush(organization);
    }

    @Test
    void ownerCanRemoveOrganizationLogo() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        String currentLogoUrl = "https://cdn.example.com/logo.png";
        organization.setLogoUrl(currentLogoUrl);
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).removeLogo();

        assertNull(response.logoUrl());
        assertNull(organization.getLogoUrl());
        verify(imageStorageService).remove(currentLogoUrl);
        verify(organizationRepository).saveAndFlush(organization);
    }

    @Test
    void ownerCanUploadOrganizationCoverImage() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        organization.setCoverImageUrl("https://cdn.example.com/old-cover.png");
        MockMultipartFile cover = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        String uploadedUrl = "https://cdn.example.com/new-cover.png";
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(imageStorageService.replace(
                "organizations/" + organization.getId() + "/cover",
                organization.getCoverImageUrl(),
                cover
        )).thenReturn(uploadedUrl);
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).uploadCoverImage(cover);

        assertEquals(uploadedUrl, response.coverImageUrl());
        assertEquals(uploadedUrl, organization.getCoverImageUrl());
        verify(organizationRepository).saveAndFlush(organization);
    }

    @Test
    void uploadingACoverImageLeavesTheLogoAlone() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        String logoUrl = "https://cdn.example.com/logo.png";
        organization.setLogoUrl(logoUrl);
        MockMultipartFile cover = new MockMultipartFile(
                "file",
                "cover.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(imageStorageService.replace(anyString(), any(), any()))
                .thenReturn("https://cdn.example.com/cover.png");
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        createService(new WebsiteUrlServiceImpl()).uploadCoverImage(cover);

        // The two images are independent; setting one must never be handed the
        // other's URL as the object to drop.
        assertEquals(logoUrl, organization.getLogoUrl());
        verify(imageStorageService, never()).remove(logoUrl);
    }

    @Test
    void ownerCanRemoveOrganizationCoverImage() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        String currentCoverUrl = "https://cdn.example.com/cover.png";
        organization.setCoverImageUrl(currentCoverUrl);
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).removeCoverImage();

        assertNull(response.coverImageUrl());
        assertNull(organization.getCoverImageUrl());
        verify(imageStorageService).remove(currentCoverUrl);
        verify(organizationRepository).saveAndFlush(organization);
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
        organization.setStatus(OrganizationStatus.ACTIVE);

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
                null,
                new InviteMemberRequest("member@acme.com", OrgRole.MEMBER)
        );

        assertEquals(MembershipStatus.SUSPENDED, response.member().status());
        assertTrue(response.member().invitationPending());
        assertNotNull(response.invitationToken());
        assertNotNull(response.expiresAt());

        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        OrganizationInvitationEmailEvent emailEvent = eventCaptor
                .getAllValues()
                .stream()
                .filter(OrganizationInvitationEmailEvent.class::isInstance)
                .map(OrganizationInvitationEmailEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("member@acme.com", emailEvent.recipientEmail());
        assertEquals("Acme Member", emailEvent.recipientName());
        assertEquals("Acme Owner", emailEvent.inviterName());
        assertEquals(OrgRole.MEMBER, emailEvent.role());
        assertEquals(response.invitationToken(), emailEvent.invitationToken());
        assertEquals(response.expiresAt(), emailEvent.expiresAt());
    }

    @Test
    void pendingOrganizationCannotInviteMembers() {
        UUID ownerId = UUID.randomUUID();
        UserProfile owner = new UserProfile();
        owner.setId(ownerId);
        owner.setEmail("owner@acme.com");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        organization.setOwner(owner);
        authenticateCompany(ownerId, owner.getEmail());
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .inviteMember(null, new InviteMemberRequest(
                                "member@acme.com",
                                OrgRole.MEMBER
                        ))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userProfileRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void myInvitationsListsOnlyTheOnesStillOpenSoonestToExpireFirst() {
        UUID userId = UUID.randomUUID();
        authenticateCompany(userId, "member@acme.com");

        UserProfile invitee = new UserProfile();
        invitee.setId(userId);
        invitee.setEmail("member@acme.com");
        invitee.setFullName("Acme Member");

        OrganizationMember expiringSoon = invitation(
                reviewOrganization(OrganizationStatus.ACTIVE),
                invitee,
                OrgRole.MANAGER,
                LocalDateTime.now().minusDays(6)
        );
        OrganizationMember expiringLater = invitation(
                reviewOrganization(OrganizationStatus.ACTIVE),
                invitee,
                OrgRole.MEMBER,
                LocalDateTime.now().minusDays(1)
        );
        OrganizationMember expired = invitation(
                reviewOrganization(OrganizationStatus.ACTIVE),
                invitee,
                OrgRole.MEMBER,
                LocalDateTime.now().minusDays(8)
        );
        OrganizationMember intoSuspendedOrganization = invitation(
                reviewOrganization(OrganizationStatus.PENDING),
                invitee,
                OrgRole.MEMBER,
                LocalDateTime.now()
        );

        when(memberRepository.findPendingInvitations(
                userId,
                MembershipStatus.SUSPENDED
        )).thenReturn(List.of(
                expiringLater,
                expired,
                intoSuspendedOrganization,
                expiringSoon
        ));

        List<PendingInvitationResponse> invitations = createService(
                new WebsiteUrlServiceImpl()
        ).getMyInvitations();

        assertEquals(2, invitations.size());
        assertEquals(
                expiringSoon.getInvitationToken(),
                invitations.get(0).invitationToken()
        );
        assertEquals(
                expiringLater.getInvitationToken(),
                invitations.get(1).invitationToken()
        );
        assertEquals(OrgRole.MANAGER, invitations.get(0).role());
        assertEquals("Acme Security", invitations.get(0).organizationName());
        assertEquals("Acme Owner", invitations.get(0).invitedByName());
        assertEquals(
                expiringSoon.getUpdatedAt().plusDays(7),
                invitations.get(0).expiresAt()
        );
    }

    @Test
    void rosterLeadsWithTheOwnerAndMarksTheCallersOwnRow() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID memberId = UUID.randomUUID();
        authenticateCompany(memberId, "member@acme.com");

        UserProfile caller = new UserProfile();
        caller.setId(memberId);
        caller.setEmail("member@acme.com");
        caller.setFullName("Acme Member");

        UserProfile colleague = new UserProfile();
        colleague.setId(UUID.randomUUID());
        colleague.setEmail("colleague@acme.com");
        colleague.setFullName("Acme Colleague");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                memberId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(membership(
                organization,
                caller,
                OrgRole.VIEWER,
                LocalDateTime.now().minusDays(1)
        )));
        when(memberRepository.findByOrganizationIdAndStatusNot(
                organization.getId(),
                MembershipStatus.REMOVED
        )).thenReturn(List.of(
                membership(
                        organization,
                        caller,
                        OrgRole.VIEWER,
                        LocalDateTime.now().minusDays(1)
                ),
                membership(
                        organization,
                        colleague,
                        OrgRole.MANAGER,
                        LocalDateTime.now().minusDays(2)
                )
        ));

        List<MemberResponse> roster = createService(
                new WebsiteUrlServiceImpl()
        ).getMyMembers(null);

        assertEquals(3, roster.size());

        MemberResponse owner = roster.get(0);
        assertTrue(owner.owner());
        assertEquals(false, owner.self());
        assertNull(owner.role());
        assertEquals(
                organization.getOwner().getId(),
                owner.userId()
        );
        assertEquals(
                EnumSet.allOf(OrganizationPermission.class),
                owner.permissions()
        );

        assertTrue(roster.get(1).self());
        assertEquals(false, roster.get(1).owner());
        assertEquals(false, roster.get(2).self());
    }

    @Test
    void rosterRefusesSomebodyWithNoOrganizationAtAll() {
        UUID outsiderId = UUID.randomUUID();
        authenticateCompany(outsiderId, "nobody@example.com");

        when(organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(outsiderId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                outsiderId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .getMyMembers(null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void managingTheTeamNeedsManageMembersAndNamesItWhenRefused() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID viewerId = UUID.randomUUID();
        authenticateCompany(viewerId, "viewer@acme.com");

        UserProfile viewer = new UserProfile();
        viewer.setId(viewerId);
        viewer.setEmail("viewer@acme.com");
        viewer.setFullName("Acme Viewer");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(viewerId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                viewerId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(membership(
                organization,
                viewer,
                OrgRole.VIEWER,
                LocalDateTime.now()
        )));

        MissingPermissionException exception = assertThrows(
                MissingPermissionException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .removeMember(null, UUID.randomUUID())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(
                OrganizationPermission.MANAGE_MEMBERS.name(),
                exception.getRequiredPermission()
        );
    }

    /**
     * A manager who can demote or remove themselves can lock a company out of
     * its own team screen, so the API refuses rather than leaving it to the
     * client to not offer it.
     */
    @Test
    void aManagerCannotActOnTheirOwnMembershipOrOnTheOwners() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID managerId = UUID.randomUUID();
        authenticateCompany(managerId, "manager@acme.com");

        UserProfile manager = new UserProfile();
        manager.setId(managerId);
        manager.setEmail("manager@acme.com");
        manager.setFullName("Acme Manager");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(managerId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                managerId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(membership(
                organization,
                manager,
                OrgRole.MANAGER,
                LocalDateTime.now()
        )));

        OrganizationServiceImpl service = createService(
                new WebsiteUrlServiceImpl()
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.removeMember(null, managerId)
                ).getStatusCode()
        );
        assertEquals(
                HttpStatus.FORBIDDEN,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.removeMember(
                                null,
                                organization.getOwner().getId()
                        )
                ).getStatusCode()
        );
        verify(memberRepository, never())
                .findByOrganizationIdAndUserId(any(), any());
    }

    /**
     * Role and permissions used to be independent, so a VIEWER could hold
     * CREATE_PROGRAM and create programs under a badge reading "Viewer".
     */
    @Test
    void aRoleWillNotHoldPermissionsBeyondWhatItAllows() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        authenticateCompany(ownerId, "owner@acme.com");

        UserProfile viewer = new UserProfile();
        viewer.setId(UUID.randomUUID());
        viewer.setEmail("viewer@acme.com");
        viewer.setFullName("Acme Viewer");
        OrganizationMember member = membership(
                organization,
                viewer,
                OrgRole.VIEWER,
                LocalDateTime.now()
        );

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(memberRepository.findByOrganizationIdAndUserId(
                organization.getId(),
                viewer.getId()
        )).thenReturn(Optional.of(member));

        DetailedApiException exception = assertThrows(
                DetailedApiException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .updateMemberPermissions(
                                null,
                                viewer.getId(),
                                new UpdateMemberPermissionsRequest(EnumSet.of(
                                        OrganizationPermission.VIEW_PROGRAMS,
                                        OrganizationPermission.CREATE_PROGRAM,
                                        OrganizationPermission.MANAGE_MEMBERS
                                ))
                        )
        );

        assertEquals(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getStatusCode()
        );
        Map<?, ?> details = (Map<?, ?>) exception.getErrorDetails();
        assertEquals(OrgRole.VIEWER.name(), details.get("role"));
        assertEquals(
                List.of(
                        OrganizationPermission.CREATE_PROGRAM.name(),
                        OrganizationPermission.MANAGE_MEMBERS.name()
                ),
                details.get("deniedPermissions")
        );
        // The whole request is refused, so nothing is half-applied.
        assertEquals(
                OrganizationPermission.defaultsFor(OrgRole.VIEWER),
                member.getPermissions()
        );
    }

    @Test
    void aRoleHoldsAnythingItsCeilingAllows() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        authenticateCompany(ownerId, "owner@acme.com");

        UserProfile teammate = new UserProfile();
        teammate.setId(UUID.randomUUID());
        teammate.setEmail("member@acme.com");
        teammate.setFullName("Acme Member");
        OrganizationMember member = membership(
                organization,
                teammate,
                OrgRole.MEMBER,
                LocalDateTime.now()
        );

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(memberRepository.findByOrganizationIdAndUserId(
                organization.getId(),
                teammate.getId()
        )).thenReturn(Optional.of(member));

        // CREATE_PROGRAM is not a MEMBER default but is within its ceiling.
        Set<OrganizationPermission> requested = EnumSet.of(
                OrganizationPermission.VIEW_PROGRAMS,
                OrganizationPermission.CREATE_PROGRAM
        );

        MemberResponse response = createService(new WebsiteUrlServiceImpl())
                .updateMemberPermissions(
                        null,
                        teammate.getId(),
                        new UpdateMemberPermissionsRequest(requested)
                );

        assertEquals(requested, response.permissions());
    }

    /**
     * Leaving the old set in place made a demotion cosmetic: a MANAGER moved
     * to VIEWER who kept MANAGE_MEMBERS is still a manager in every way that
     * matters.
     */
    @Test
    void changingARoleResetsThePermissionsToThatRolesDefaults() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        organization.getOwner().setId(ownerId);
        authenticateCompany(ownerId, "owner@acme.com");

        UserProfile manager = new UserProfile();
        manager.setId(UUID.randomUUID());
        manager.setEmail("manager@acme.com");
        manager.setFullName("Acme Manager");
        OrganizationMember member = membership(
                organization,
                manager,
                OrgRole.MANAGER,
                LocalDateTime.now()
        );

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(memberRepository.findByOrganizationIdAndUserId(
                organization.getId(),
                manager.getId()
        )).thenReturn(Optional.of(member));

        MemberResponse response = createService(new WebsiteUrlServiceImpl())
                .updateMemberRole(
                        null,
                        manager.getId(),
                        new UpdateMemberRoleRequest(OrgRole.VIEWER)
                );

        assertEquals(OrgRole.VIEWER, response.role());
        assertEquals(
                OrganizationPermission.defaultsFor(OrgRole.VIEWER),
                response.permissions()
        );
        assertEquals(false, member.hasPermission(
                OrganizationPermission.MANAGE_MEMBERS
        ));
    }

    /**
     * Answering with either organization would be a guess and answering with
     * the first would quietly hide the other, so the caller is asked — and
     * handed the ids it needs to ask with.
     */
    @Test
    void anAccountAtTwoCompaniesIsAskedWhichOneAndCanNameIt() {
        UUID memberId = UUID.randomUUID();
        authenticateCompany(memberId, "member@acme.com");

        UserProfile caller = new UserProfile();
        caller.setId(memberId);
        caller.setEmail("member@acme.com");
        caller.setFullName("Acme Member");

        Organization first = reviewOrganization(OrganizationStatus.ACTIVE);
        Organization second = reviewOrganization(OrganizationStatus.ACTIVE);

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                memberId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(
                membership(
                        first,
                        caller,
                        OrgRole.VIEWER,
                        LocalDateTime.now().minusDays(2)
                ),
                membership(
                        second,
                        caller,
                        OrgRole.MEMBER,
                        LocalDateTime.now().minusDays(1)
                )
        ));

        OrganizationServiceImpl service = createService(
                new WebsiteUrlServiceImpl()
        );

        DetailedApiException exception = assertThrows(
                DetailedApiException.class,
                () -> service.getMyMembers(null)
        );
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        Map<?, ?> details = (Map<?, ?>) exception.getErrorDetails();
        assertEquals("organizationId", details.get("parameter"));
        assertEquals(
                List.of(first.getId().toString(), second.getId().toString()),
                details.get("organizationIds")
        );

        when(memberRepository.findByOrganizationIdAndStatusNot(
                second.getId(),
                MembershipStatus.REMOVED
        )).thenReturn(List.of());

        List<MemberResponse> roster = service.getMyMembers(second.getId());

        assertEquals(1, roster.size());
        assertEquals(second.getOwner().getId(), roster.getFirst().userId());
    }

    @Test
    void namingAnOrganizationTheCallerIsNotAtIsRefused() {
        UUID memberId = UUID.randomUUID();
        authenticateCompany(memberId, "member@acme.com");

        UserProfile caller = new UserProfile();
        caller.setId(memberId);
        caller.setEmail("member@acme.com");
        caller.setFullName("Acme Member");

        Organization theirs = reviewOrganization(OrganizationStatus.ACTIVE);

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                memberId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(membership(
                theirs,
                caller,
                OrgRole.VIEWER,
                LocalDateTime.now()
        )));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .getMyMembers(UUID.randomUUID())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    /**
     * A member who accepted an invitation works at the company too, and used
     * to be told "you do not own an organization" by every screen built on
     * this endpoint.
     */
    @Test
    void theWorkspaceOpensForAMemberAndNotOnlyTheOwner() {
        UUID memberId = UUID.randomUUID();
        authenticateCompany(memberId, "member@acme.com");

        UserProfile caller = new UserProfile();
        caller.setId(memberId);
        caller.setEmail("member@acme.com");
        caller.setFullName("Acme Member");

        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(memberId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                memberId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(membership(
                organization,
                caller,
                OrgRole.VIEWER,
                LocalDateTime.now()
        )));
        when(reportRewardRepository.findOrganizationPayouts(
                organization.getId()
        )).thenReturn(payouts("0", "0"));

        assertEquals(
                organization.getId(),
                createService(new WebsiteUrlServiceImpl()).me(null).id()
        );
    }

    @Test
    void thePublishedRoleTableMatchesWhatTheApiEnforces() {
        List<OrganizationRoleResponse> roles = createService(
                new WebsiteUrlServiceImpl()
        ).getRoles();

        assertEquals(OrgRole.values().length, roles.size());
        roles.forEach(role -> {
            assertEquals(
                    OrganizationPermission.defaultsFor(role.role()),
                    role.defaultPermissions()
            );
            assertEquals(
                    OrganizationPermission.ceilingFor(role.role()),
                    role.allowedPermissions()
            );
            // A member starts inside their own ceiling, or the API would
            // refuse a set it had just handed out.
            assertTrue(role.allowedPermissions()
                    .containsAll(role.defaultPermissions()));
        });
    }

    @Test
    void myMembershipsCarriesTheOwnedOrganizationAndTheAcceptedInvitations() {
        UUID userId = UUID.randomUUID();
        authenticateCompany(userId, "member@acme.com");

        UserProfile member = new UserProfile();
        member.setId(userId);
        member.setEmail("member@acme.com");
        member.setFullName("Acme Member");

        Organization owned = reviewOrganization(OrganizationStatus.PENDING);
        owned.getOwner().setId(userId);

        Organization joinedFirst = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        Organization joinedLater = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        Organization deleted = reviewOrganization(OrganizationStatus.ACTIVE);
        deleted.setDeletedAt(LocalDateTime.now());

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(owned));
        when(memberRepository.findByUserIdAndStatus(
                userId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of(
                membership(
                        joinedLater,
                        member,
                        OrgRole.VIEWER,
                        LocalDateTime.now().minusDays(1)
                ),
                membership(
                        deleted,
                        member,
                        OrgRole.MANAGER,
                        LocalDateTime.now().minusDays(3)
                ),
                membership(
                        joinedFirst,
                        member,
                        OrgRole.MEMBER,
                        LocalDateTime.now().minusDays(2)
                )
        ));

        List<OrganizationMembershipResponse> memberships = createService(
                new WebsiteUrlServiceImpl()
        ).getMyMemberships();

        assertEquals(3, memberships.size());

        OrganizationMembershipResponse ownedMembership = memberships.get(0);
        assertEquals(owned.getId(), ownedMembership.organizationId());
        assertTrue(ownedMembership.owner());
        assertNull(ownedMembership.role());
        assertEquals(
                EnumSet.allOf(OrganizationPermission.class),
                ownedMembership.permissions()
        );
        // The company is still under review, and the client is told so rather
        // than shown nothing.
        assertEquals(
                OrganizationStatus.PENDING,
                ownedMembership.organizationStatus()
        );

        assertEquals(joinedFirst.getId(), memberships.get(1).organizationId());
        assertEquals(OrgRole.MEMBER, memberships.get(1).role());
        assertTrue(memberships.get(1).permissions().contains(
                OrganizationPermission.TRIAGE_REPORTS
        ));
        assertEquals(false, memberships.get(1).owner());

        assertEquals(joinedLater.getId(), memberships.get(2).organizationId());
        assertEquals(
                OrganizationPermission.defaultsFor(OrgRole.VIEWER),
                memberships.get(2).permissions()
        );
    }

    @Test
    void myMembershipsIsEmptyForAnAccountAtNoCompany() {
        UUID userId = UUID.randomUUID();
        authenticateCompany(userId, "researcher@example.com");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.empty());
        when(memberRepository.findByUserIdAndStatus(
                userId,
                MembershipStatus.ACTIVE
        )).thenReturn(List.of());

        assertTrue(createService(new WebsiteUrlServiceImpl())
                .getMyMemberships()
                .isEmpty());
    }

    @Test
    void adminListsAndFiltersAllOrganizationsNewestFirst() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(organizationRepository.findForAdmin(
                eq("%acme%"),
                eq(OrganizationStatus.ACTIVE),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(organization)));

        Page<OrganizationReviewSummaryResponse> result =
                createService(new WebsiteUrlServiceImpl())
                        .getOrganizationsForAdmin(
                                "  AcMe  ",
                                OrganizationStatus.ACTIVE,
                                1,
                                25
                        );

        OrganizationReviewSummaryResponse response =
                result.getContent().getFirst();
        assertEquals(organization.getId(), response.id());
        assertEquals(OrganizationStatus.ACTIVE, response.status());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(25, pageableCaptor.getValue().getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageableCaptor.getValue().getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void nonAdminCannotListAllOrganizations() {
        authenticate("COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .getOrganizationsForAdmin(null, null, 0, 20)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(organizationRepository, never()).findForAdmin(
                any(),
                any(),
                any()
        );
    }

    @Test
    void adminListsPendingOrganizationsOldestFirst() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(organizationRepository.findByStatusAndDeletedAtIsNull(
                eq(OrganizationStatus.PENDING),
                pageableCaptor.capture()
        )).thenReturn(new PageImpl<>(List.of(organization)));

        Page<OrganizationReviewSummaryResponse> result =
                createService(new WebsiteUrlServiceImpl())
                        .getPendingOrganizations(0, 20);

        OrganizationReviewSummaryResponse response =
                result.getContent().getFirst();
        assertEquals(organization.getId(), response.id());
        assertEquals(organization.getOwner().getEmail(), response.ownerEmail());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        assertEquals(
                Sort.Direction.ASC,
                pageableCaptor.getValue().getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
        verify(companyIdentityService, never()).isEmailVerified(any());
    }

    @Test
    void nonAdminCannotListPendingOrganizations() {
        authenticate("COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .getPendingOrganizations(0, 20)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(organizationRepository, never())
                .findByStatusAndDeletedAtIsNull(
                        any(),
                        any()
                );
    }

    @Test
    void adminGetsOrganizationReviewDetail() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        when(organizationRepository.findByIdAndDeletedAtIsNull(
                organization.getId()
        )).thenReturn(Optional.of(organization));
        when(companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        )).thenReturn(true);

        OrganizationReviewResponse response =
                createService(new WebsiteUrlServiceImpl())
                        .getForReview(organization.getId());

        assertEquals(organization.getId(), response.id());
        assertEquals("Acme Owner", response.ownerFullName());
        assertEquals("owner@acme.com", response.ownerEmail());
        assertEquals("Security Manager", response.ownerJobTitle());
        assertEquals(
                "We want to run a responsible disclosure program.",
                response.joiningReason()
        );
        assertTrue(response.emailVerified());
    }

    @Test
    void adminApprovesPendingOrganizationWithVerifiedEmail() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        when(organizationRepository.findByIdForReview(
                organization.getId()
        )).thenReturn(Optional.of(organization));
        when(companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        )).thenReturn(true);

        OrganizationResponse response =
                createService(new WebsiteUrlServiceImpl())
                        .approve(organization.getId());

        assertEquals(OrganizationStatus.ACTIVE, response.status());
        assertEquals(OrganizationStatus.ACTIVE, organization.getStatus());
        assertNotNull(response.verifiedAt());
        assertNotNull(response.reviewedAt());
        assertNotNull(organization.getReviewedBy());
        ArgumentCaptor<OrganizationReviewHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationReviewHistory.class);
        verify(reviewHistoryRepository).save(historyCaptor.capture());
        assertEquals(
                OrganizationReviewDecision.APPROVED,
                historyCaptor.getValue().getDecision()
        );
    }

    @Test
    void adminCannotApproveOrganizationWithUnverifiedEmail() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        when(organizationRepository.findByIdForReview(
                organization.getId()
        )).thenReturn(Optional.of(organization));
        when(companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .approve(organization.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrganizationStatus.PENDING, organization.getStatus());
        assertNull(organization.getVerifiedAt());
    }

    @Test
    void adminRejectsPendingOrganization() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        organization.setVerifiedAt(LocalDateTime.now());
        when(organizationRepository.findByIdForReview(
                organization.getId()
        )).thenReturn(Optional.of(organization));

        OrganizationResponse response =
                createService(new WebsiteUrlServiceImpl())
                        .reject(
                                organization.getId(),
                                new RejectOrganizationRequest(
                                        "Unable to verify the company information"
                                )
                        );

        assertEquals(OrganizationStatus.REJECTED, response.status());
        assertEquals(OrganizationStatus.REJECTED, organization.getStatus());
        assertNull(response.verifiedAt());
        assertEquals(
                "Unable to verify the company information",
                response.rejectionReason()
        );
        ArgumentCaptor<OrganizationReviewHistory> historyCaptor =
                ArgumentCaptor.forClass(OrganizationReviewHistory.class);
        verify(reviewHistoryRepository).save(historyCaptor.capture());
        assertEquals(
                OrganizationReviewDecision.REJECTED,
                historyCaptor.getValue().getDecision()
        );
        assertEquals(
                response.rejectionReason(),
                historyCaptor.getValue().getReason()
        );
        verify(companyIdentityService, never()).isEmailVerified(any());
    }

    @Test
    void rejectedOrganizationCanResubmitForAnotherReview() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.REJECTED
        );
        organization.getOwner().setId(ownerId);
        organization.setSubmissionVersion(1);
        organization.setReviewedBy(UUID.randomUUID());
        organization.setReviewedAt(LocalDateTime.now());
        organization.setRejectionReason("Please correct the company website");
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).resubmit();

        assertEquals(OrganizationStatus.PENDING, response.status());
        assertEquals(2, response.submissionVersion());
        assertNull(response.rejectionReason());
        assertNull(response.reviewedAt());
        ArgumentCaptor<OrganizationLifecycleEvent> eventCaptor =
                ArgumentCaptor.forClass(OrganizationLifecycleEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(
                OrganizationLifecycleEventType.RESUBMITTED,
                eventCaptor.getValue().type()
        );
        assertEquals(2, eventCaptor.getValue().submissionVersion());
    }

    @Test
    void verificationStatusTellsPendingOwnerToVerifyEmail() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        organization.getOwner().setId(ownerId);
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(companyIdentityService.isEmailVerified(ownerId))
                .thenReturn(false);

        OrganizationVerificationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).getVerificationStatus();

        assertEquals(OrganizationNextAction.VERIFY_EMAIL, response.nextAction());
        assertEquals(OrganizationStatus.PENDING, response.organizationStatus());
    }

    @Test
    void verificationEmailResendIsRateLimited() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.PENDING
        );
        organization.getOwner().setId(ownerId);
        organization.setVerificationEmailSentAt(LocalDateTime.now());
        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(companyIdentityService.isEmailVerified(ownerId))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .resendVerificationEmail()
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        verify(companyIdentityService, never()).sendVerificationEmail(any());
    }

    @Test
    void rejectedOrganizationMustReturnToPendingBeforeApproval() {
        authenticate("ADMIN");
        Organization organization = reviewOrganization(
                OrganizationStatus.REJECTED
        );
        when(organizationRepository.findByIdForReview(
                organization.getId()
        )).thenReturn(Optional.of(organization));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> createService(new WebsiteUrlServiceImpl())
                        .approve(organization.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(OrganizationStatus.REJECTED, organization.getStatus());
        verify(companyIdentityService, never()).isEmailVerified(any());
    }

    private OrganizationServiceImpl createService(
            WebsiteUrlService websiteUrlService
    ) {
        return new OrganizationServiceImpl(
                organizationRepository,
                memberRepository,
                userProfileRepository,
                new OrganizationMapper(websiteUrlService),
                new OrganizationAuthorizationService(
                        organizationRepository,
                        memberRepository
                ),
                usernameAllocator,
                websiteUrlService,
                imageStorageService,
                companyIdentityService,
                reviewHistoryRepository,
                eventPublisher,
                programRepository,
                programService,
                reportRepository,
                reportRewardRepository
        );
    }

    private OrganizationRequest validRequest() {
        return new OrganizationRequest(
                "Acme Owner",
                "Security Manager",
                "owner@acme.com",
                "+855 12 345 678",
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

    private OrganizationRequest requestWithPhone(String phone) {
        OrganizationRequest request = validRequest();
        return new OrganizationRequest(
                request.fullName(),
                request.jobTitle(),
                request.email(),
                phone,
                request.password(),
                request.confirmPassword(),
                request.companyName(),
                request.companyWebsite(),
                request.industry(),
                request.companySize(),
                request.country(),
                request.joiningReason()
        );
    }

    private Organization reviewOrganization(OrganizationStatus status) {
        UserProfile owner = new UserProfile();
        owner.setId(UUID.randomUUID());
        owner.setFullName("Acme Owner");
        owner.setEmail("owner@acme.com");

        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setOwner(owner);
        organization.setName("Acme Security");
        organization.setSlug("acme-security");
        organization.setWebsiteUrl("https://www.acme.com");
        organization.setIndustry(Industry.TECHNOLOGY);
        organization.setCompanySize("11-50");
        organization.setCountry("Cambodia");
        organization.setOwnerJobTitle("Security Manager");
        organization.setJoiningReason(
                "We want to run a responsible disclosure program."
        );
        organization.setStatus(status);
        organization.setCreatedAt(LocalDateTime.now().minusDays(1));
        organization.setUpdatedAt(LocalDateTime.now());
        return organization;
    }

    private OrganizationMember membership(
            Organization organization,
            UserProfile user,
            OrgRole role,
            LocalDateTime joinedAt
    ) {
        OrganizationMember member = new OrganizationMember(
                organization,
                user,
                role
        );
        member.setStatus(MembershipStatus.ACTIVE);
        member.setInvitedBy(organization.getOwner());
        member.setInvitationEmail(user.getEmail());
        member.setJoinedAt(joinedAt);
        return member;
    }

    private OrganizationMember invitation(
            Organization organization,
            UserProfile invitee,
            OrgRole role,
            LocalDateTime issuedAt
    ) {
        OrganizationMember member = new OrganizationMember(
                organization,
                invitee,
                role
        );
        member.setStatus(MembershipStatus.SUSPENDED);
        member.setInvitedBy(organization.getOwner());
        member.setInvitationEmail(invitee.getEmail());
        member.setInvitationToken(UUID.randomUUID().toString());
        member.setCreatedAt(issuedAt);
        member.setUpdatedAt(issuedAt);
        return member;
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim(
                        "realm_access",
                        Map.of("roles", List.of(role))
                )
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }

    // ------------------------------------------------------ profile statistics

    private ReportRewardRepository.OrganizationPayouts payouts(
            String total,
            String top
    ) {
        return new ReportRewardRepository.OrganizationPayouts() {
            @Override
            public BigDecimal getTotalDisbursed() {
                return new BigDecimal(total);
            }

            @Override
            public BigDecimal getTopAward() {
                return new BigDecimal(top);
            }
        };
    }

    @Test
    void publicProfileCarriesTheHeadlineNumbers() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID organizationId = organization.getId();

        when(organizationRepository.findByIdAndStatusAndDeletedAtIsNull(
                organizationId,
                OrganizationStatus.ACTIVE
        )).thenReturn(Optional.of(organization));
        when(programRepository
                .countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
                        organizationId,
                        ProgramState.ACTIVE,
                        Visibility.PUBLIC
                )).thenReturn(3L);
        when(reportRepository.countByOrganizationAndState(
                organizationId,
                ReportState.RESOLVED
        )).thenReturn(42L);
        when(reportRewardRepository.findOrganizationPayouts(organizationId))
                .thenReturn(payouts("12500.00", "4000.00"));

        OrganizationStatsResponse stats = createService(
                new WebsiteUrlServiceImpl()
        ).getById(organizationId).stats();

        assertEquals(3L, stats.activePrograms());
        assertEquals(42L, stats.resolvedReports());
        assertEquals(new BigDecimal("12500.00"), stats.totalDisbursed());
        assertEquals(new BigDecimal("4000.00"), stats.topBountyAward());
    }

    @Test
    void publicProfileDoesNotCountPrivatePrograms() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID organizationId = organization.getId();

        when(organizationRepository.findByIdAndStatusAndDeletedAtIsNull(
                organizationId,
                OrganizationStatus.ACTIVE
        )).thenReturn(Optional.of(organization));
        when(reportRewardRepository.findOrganizationPayouts(organizationId))
                .thenReturn(payouts("0", "0"));

        createService(new WebsiteUrlServiceImpl()).getById(organizationId);

        // The count beside a program list that hides private programs has to
        // agree with it.
        verify(programRepository)
                .countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
                        organizationId,
                        ProgramState.ACTIVE,
                        Visibility.PUBLIC
                );
        verify(programRepository, never())
                .countByOrganizationIdAndStateAndDeletedAtIsNull(any(), any());
    }

    @Test
    void ownProfileCountsPrivateProgramsToo() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID organizationId = organization.getId();

        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(programRepository
                .countByOrganizationIdAndStateAndDeletedAtIsNull(
                        organizationId,
                        ProgramState.ACTIVE
                )).thenReturn(7L);
        when(reportRewardRepository.findOrganizationPayouts(organizationId))
                .thenReturn(payouts("0", "0"));

        OrganizationStatsResponse stats = createService(
                new WebsiteUrlServiceImpl()
        ).me(null).stats();

        assertEquals(7L, stats.activePrograms());
        verify(programRepository, never())
                .countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
                        any(), any(), any()
                );
    }

    @Test
    void organizationThatHasPaidNothingReadsZeroNotNull() {
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );
        UUID organizationId = organization.getId();

        when(organizationRepository.findByIdAndStatusAndDeletedAtIsNull(
                organizationId,
                OrganizationStatus.ACTIVE
        )).thenReturn(Optional.of(organization));
        when(reportRewardRepository.findOrganizationPayouts(organizationId))
                .thenReturn(payouts("0", "0"));

        OrganizationStatsResponse stats = createService(
                new WebsiteUrlServiceImpl()
        ).getById(organizationId).stats();

        assertEquals(0, stats.totalDisbursed().signum());
        assertEquals(0, stats.topBountyAward().signum());
        assertEquals(0L, stats.activePrograms());
        assertEquals(0L, stats.resolvedReports());
    }

    @Test
    void mutationResponsesDoNotPayForStatistics() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = reviewOrganization(
                OrganizationStatus.ACTIVE
        );

        authenticateCompany(ownerId, "owner@acme.com");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.saveAndFlush(organization))
                .thenReturn(organization);

        OrganizationResponse response = createService(
                new WebsiteUrlServiceImpl()
        ).removeLogo();

        assertNull(response.stats());
        verifyNoInteractions(reportRewardRepository);
    }

    private void authenticateCompany(UUID userId, String email) {
        Jwt jwt = Jwt.withTokenValue("company-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", email)
                .claim("realm_access", Map.of("roles", List.of("COMPANY")))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
                )
        );
    }
}
