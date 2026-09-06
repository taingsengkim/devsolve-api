package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RemoveResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherRemovalResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherRemovalScope;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.ProgramInvitationService;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramAccessRevocationResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Removing a researcher from a company, which is two records and one decision.
 *
 * <p>The case behind it is a researcher uploading a file a scanner called
 * malicious. What the company means by "remove them" is that the person stops
 * being able to submit — and getting that wrong in the quiet direction, by
 * closing the private half and leaving the public half standing, looks from the
 * console exactly like it worked.
 */
@ExtendWith(MockitoExtension.class)
class ResearcherRemovalTest {

    @Mock
    private OrganizationResearcherRepository researcherAccessRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramInvitationService programInvitationService;

    @Mock
    private OrganizationAuthorizationService organizationAuthorization;

    @Mock
    private ResearcherAccessMapper mapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ResearcherAccessServiceImpl service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID researcherId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ResearcherAccessServiceImpl(
                researcherAccessRepository,
                organizationRepository,
                userProfileRepository,
                programRepository,
                programInvitationService,
                organizationAuthorization,
                mapper,
                eventPublisher
        );
        authenticate(actorId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The narrower scope. Their presence on one confidential engagement was the
     * objection, so their standing with the company survives and they can still
     * report to its public programs.
     */
    @Test
    void removingFromPrivateProgramsLeavesTheCompanyStandingAlone() {
        when(programInvitationService.revokeAllForOrganization(
                organizationId,
                researcherId
        )).thenReturn(revocation(2));

        ResearcherRemovalResponse response = service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.PRIVATE_PROGRAMS,
                        "Off this engagement."
                )
        );

        assertEquals(2, response.privatePrograms().revoked());
        assertFalse(response.organizationAccessRevoked());
        assertNull(response.previousOrganizationStatus());
        // The org record is never even read, let alone written.
        verify(researcherAccessRepository, never())
                .findByOrganizationIdAndResearcherId(any(), any());
        verify(researcherAccessRepository, never())
                .saveAndFlush(any(OrganizationResearcher.class));
    }

    /**
     * The one a malicious upload reaches for. Both halves close, so the
     * researcher can no longer submit to this company at all — public programs
     * included, which is the half the private-program action leaves standing.
     */
    @Test
    void removingFromTheEntireCompanyClosesPublicProgramsToo() {
        OrganizationResearcher access = approvedAccess();

        when(programInvitationService.revokeAllForOrganization(
                organizationId,
                researcherId
        )).thenReturn(revocation(1));
        when(researcherAccessRepository.findByOrganizationIdAndResearcherId(
                organizationId,
                researcherId
        )).thenReturn(Optional.of(access));
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(user(actorId)));
        when(researcherAccessRepository.saveAndFlush(access))
                .thenReturn(access);

        ResearcherRemovalResponse response = service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.ENTIRE_COMPANY,
                        "Uploaded a file flagged as malicious."
                )
        );

        assertTrue(response.organizationAccessRevoked());
        assertEquals(
                ResearcherAccessStatus.APPROVED,
                response.previousOrganizationStatus()
        );
        assertEquals(ResearcherAccessStatus.REVOKED, access.getStatus());
        assertEquals(
                "Uploaded a file flagged as malicious.",
                access.getReviewNote()
        );
        // The private half goes too. Ending somebody's access to everything has
        // certainly ended it to the confidential programs.
        assertEquals(1, response.privatePrograms().revoked());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    /**
     * Forgiving where {@code review(REVOKE)} is strict. That path refuses to
     * revoke somebody who was never approved; here the company is closing
     * everything this person holds, whatever state each part is in.
     */
    @Test
    void aPendingRequestIsClosedRatherThanRefused() {
        OrganizationResearcher access = approvedAccess();
        access.setStatus(ResearcherAccessStatus.PENDING);

        when(programInvitationService.revokeAllForOrganization(any(), any()))
                .thenReturn(revocation(0));
        when(researcherAccessRepository.findByOrganizationIdAndResearcherId(
                organizationId,
                researcherId
        )).thenReturn(Optional.of(access));
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(user(actorId)));
        when(researcherAccessRepository.saveAndFlush(access))
                .thenReturn(access);

        ResearcherRemovalResponse response = service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.ENTIRE_COMPANY,
                        null
                )
        );

        assertTrue(response.organizationAccessRevoked());
        assertEquals(
                ResearcherAccessStatus.PENDING,
                response.previousOrganizationStatus()
        );
        assertEquals(ResearcherAccessStatus.REVOKED, access.getStatus());
    }

    /**
     * A researcher who never held clearance could not submit to the public
     * programs to begin with, so the company's intent is already satisfied.
     */
    @Test
    void aResearcherWithNoStandingIsNotAnError() {
        when(programInvitationService.revokeAllForOrganization(any(), any()))
                .thenReturn(revocation(0));
        when(researcherAccessRepository.findByOrganizationIdAndResearcherId(
                organizationId,
                researcherId
        )).thenReturn(Optional.empty());

        ResearcherRemovalResponse response = service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.ENTIRE_COMPANY,
                        null
                )
        );

        assertFalse(response.organizationAccessRevoked());
        assertNull(response.previousOrganizationStatus());
        verify(researcherAccessRepository, never())
                .saveAndFlush(any(OrganizationResearcher.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /** Already revoked is a no-op, not a second notification. */
    @Test
    void anAlreadyRevokedStandingIsLeftAlone() {
        OrganizationResearcher access = approvedAccess();
        access.setStatus(ResearcherAccessStatus.REVOKED);

        when(programInvitationService.revokeAllForOrganization(any(), any()))
                .thenReturn(revocation(0));
        when(researcherAccessRepository.findByOrganizationIdAndResearcherId(
                organizationId,
                researcherId
        )).thenReturn(Optional.of(access));

        ResearcherRemovalResponse response = service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.ENTIRE_COMPANY,
                        null
                )
        );

        assertFalse(response.organizationAccessRevoked());
        verify(researcherAccessRepository, never())
                .saveAndFlush(any(OrganizationResearcher.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    /** Removing anybody needs the permission that manages researchers. */
    @Test
    void removalNeedsTheResearcherManagementPermission() {
        when(programInvitationService.revokeAllForOrganization(any(), any()))
                .thenReturn(revocation(0));
        when(researcherAccessRepository.findByOrganizationIdAndResearcherId(
                any(),
                any()
        )).thenReturn(Optional.empty());

        service.removeResearcher(
                organizationId,
                researcherId,
                new RemoveResearcherRequest(
                        ResearcherRemovalScope.ENTIRE_COMPANY,
                        null
                )
        );

        verify(organizationAuthorization).requirePermission(
                eq(organizationId),
                eq(actorId),
                eq(OrganizationPermission.MANAGE_RESEARCHERS)
        );
    }

    private ProgramAccessRevocationResponse revocation(int revoked) {
        return new ProgramAccessRevocationResponse(
                organizationId,
                researcherId,
                revoked,
                List.of()
        );
    }

    private OrganizationResearcher approvedAccess() {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("Acme");
        OrganizationResearcher access = new OrganizationResearcher(
                organization,
                user(researcherId)
        );
        access.setId(UUID.randomUUID());
        access.setStatus(ResearcherAccessStatus.APPROVED);
        return access;
    }

    private UserProfile user(UUID id) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setFullName("Ada Lovelace");
        return profile;
    }

    private void authenticate(UUID subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setName("Acme");
        lenient().when(organizationAuthorization.requirePermission(
                any(),
                any(),
                any()
        )).thenReturn(organization);
    }
}
