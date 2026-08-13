package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.RejectOrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
                new InviteMemberRequest("member@acme.com", OrgRole.MEMBER)
        );

        assertEquals(MembershipStatus.SUSPENDED, response.member().status());
        assertTrue(response.member().invitationPending());
        assertNotNull(response.invitationToken());
        assertNotNull(response.expiresAt());
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
                        .inviteMember(new InviteMemberRequest(
                                "member@acme.com",
                                OrgRole.MEMBER
                        ))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userProfileRepository, never()).findByEmailIgnoreCase(any());
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
        ).me().stats();

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
