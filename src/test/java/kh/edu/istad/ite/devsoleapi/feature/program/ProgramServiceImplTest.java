package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.common.listing.ViewCountGuard;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceImplTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramAssetRepository programAssetRepository;

    @Mock
    private ProgramUpdateRepository programUpdateRepository;

    @Mock
    private ProgramMapper programMapper;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private FollowNotificationService followNotificationService;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ViewCountGuard viewCountGuard;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private kh.edu.istad.ite.devsoleapi.feature.organization
            .CompanyIdentityService companyIdentityService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProgramUsesOwnedOrganizationAndPendingDefaults() {
        UUID userId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(userId);
        Program program = validProgram(organization.getId());
        authenticate(userId, "COMPANY");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(organization));
        when(programMapper.toEntity(any(ProgramRequestDto.class)))
                .thenReturn(program);
        when(programRepository.saveAndFlush(program)).thenReturn(program);

        service().createProgram(ProgramRequestDto.builder()
                .handle("acme-security")
                .name("Acme Security Program")
                .engagementType(EngagementType.BOUNTY)
                .visibility(Visibility.PRIVATE)
                .build());

        assertEquals(organization.getId(), program.getOrganizationId());
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
        verify(programRepository).saveAndFlush(program);
        verify(programUpdateRepository).save(any(ProgramUpdate.class));
    }

    @Test
    void createProgramHonoursRequestedActiveStateButStillNeedsReview() {
        UUID userId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(userId);
        Program program = validProgram(organization.getId());
        authenticate(userId, "COMPANY");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(organization));
        when(programMapper.toEntity(any(ProgramRequestDto.class)))
                .thenReturn(program);
        when(programRepository.saveAndFlush(program)).thenReturn(program);

        service().createProgram(ProgramRequestDto.builder()
                .handle("acme-security")
                .name("Acme Security Program")
                .engagementType(EngagementType.BOUNTY)
                .visibility(Visibility.PRIVATE)
                .state(ProgramState.ACTIVE)
                .build());

        assertEquals(ProgramState.ACTIVE, program.getState());
        // Launching early settles what happens after approval; it never
        // stands in for the approval itself.
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
    }

    @Test
    void createProgramRejectsAStartingStateThatIsNotDraftOrActive() {
        UUID userId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(userId);
        authenticate(userId, "COMPANY");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(organization));
        when(programMapper.toEntity(any(ProgramRequestDto.class)))
                .thenReturn(validProgram(organization.getId()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().createProgram(ProgramRequestDto.builder()
                        .handle("acme-security")
                        .name("Acme Security Program")
                        .engagementType(EngagementType.BOUNTY)
                        .visibility(Visibility.PRIVATE)
                        .state(ProgramState.CLOSED)
                        .build())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(programRepository, never()).saveAndFlush(any(Program.class));
    }

    @Test
    void ownerGetsCompleteSavedDraftForEditing() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setDescription("Full draft description");
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setVisibility(Visibility.PRIVATE);
        authenticate(ownerId, "COMPANY");

        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        var response = service(new ProgramMapper())
                .getMyProgram(program.getId());

        assertEquals("Full draft description", response.description());
        assertEquals(EngagementType.BOUNTY, response.engagementType());
        assertEquals(Visibility.PRIVATE, response.visibility());
        assertEquals(program.getPolicy(), response.policy());
        assertEquals(
                program.getProofOfConceptRequirements(),
                response.proofOfConceptRequirements()
        );
        assertEquals(
                program.getRulesOfEngagement(),
                response.rulesOfEngagement()
        );
        assertEquals(program.getExclusions(), response.exclusions());
        assertEquals(program.getOffersBounties(), response.offersBounties());
        assertEquals(1, response.assets().size());
    }

    @Test
    void userWithoutProgramAccessCannotGetSavedDraft() {
        UUID otherUserId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(UUID.randomUUID());
        Program program = validProgram(organization.getId());
        authenticate(otherUserId, "COMPANY");

        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository.findByOrganizationIdAndUserId(
                organization.getId(),
                otherUserId
        )).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(new ProgramMapper())
                        .getMyProgram(program.getId())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void myProgramsIncludeDescriptionAndAllAssetsFromOneBulkLoad() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setDescription("Full saved draft description");
        ProgramAsset inScope = program.getAssets().getFirst();
        ProgramAsset outOfScope = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://legacy.acme.test")
                .isInScope(false)
                .build();
        PageRequest pageable = PageRequest.of(0, 20);
        authenticate(ownerId, "COMPANY");

        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(programRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Program>>any(),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(programAssetRepository.findByProgramIds(Set.of(program.getId())))
                .thenReturn(List.of(inScope, outOfScope));

        var response = service(new ProgramMapper())
                .getMyPrograms(pageable)
                .getContent()
                .getFirst();

        assertEquals(
                "Full saved draft description",
                response.description()
        );
        assertEquals(2, response.assets().size());
        assertTrue(response.assets().stream().anyMatch(asset ->
                Boolean.FALSE.equals(asset.isInScope())
                        && "https://legacy.acme.test".equals(
                                asset.identifier()
                        )
        ));
        verify(programAssetRepository).findByProgramIds(
                Set.of(program.getId())
        );
    }

    @Test
    void createProgramAllowsPublicVisibilityWhilePendingApproval() {
        UUID userId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(userId);
        Program program = validProgram(organization.getId());
        program.setVisibility(Visibility.PUBLIC);
        authenticate(userId, "COMPANY");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(organization));
        when(programMapper.toEntity(any(ProgramRequestDto.class)))
                .thenReturn(program);
        when(programRepository.saveAndFlush(program)).thenReturn(program);

        service().createProgram(ProgramRequestDto.builder()
                .handle("public-before-approval")
                .name("Public Program Awaiting Approval")
                .engagementType(EngagementType.BOUNTY)
                .visibility(Visibility.PUBLIC)
                .build());

        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
        assertEquals(Visibility.PUBLIC, program.getVisibility());
        verify(programRepository).saveAndFlush(program);
    }

    @Test
    void pendingProgramCanChangeVisibilityWithoutAnotherReview() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setVisibility(Visibility.PRIVATE);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        ProgramUpdateRequestDto request = new ProgramUpdateRequestDto(
                null,
                null,
                null,
                null,
                Visibility.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        doAnswer(invocation -> {
            Program target = invocation.getArgument(1);
            target.setVisibility(request.visibility());
            return null;
        }).when(programMapper).updateEntity(eq(request), eq(program));

        service().updateProgram(program.getId(), request);

        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(Visibility.PUBLIC, program.getVisibility());
        verifyNoInteractions(followNotificationService);
    }

    @Test
    void adminCanApprovePendingProgram() {
        UUID adminId = UUID.randomUUID();
        Organization organization =
                activeOwnedOrganization(UUID.randomUUID());
        Program program = validProgram(organization.getId());
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        authenticate(adminId, "ADMIN");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().approveProgram(program.getId());

        assertEquals(
                SubmissionState.APPROVED,
                program.getSubmissionState()
        );
        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(adminId, updateCaptor.getValue().getChangedBy());
    }

    @Test
    void editingApprovedProgramProofRequirementsRequiresReviewAgain() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service(new ProgramMapper()).updateProgram(
                program.getId(),
                new ProgramUpdateRequestDto(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new ProgramGuidelinesDto(
                                "Provide a working exploit.",
                                List.of("Attach an HTTP request trace")
                        ),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
    }

    @Test
    void identicalFullSaveDoesNotRestartReviewOrWriteAChangeLog() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        program.setPublishedAt(LocalDateTime.now());
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        ProgramAsset asset = program.getAssets().getFirst();
        ProgramUpdateRequestDto request = new ProgramUpdateRequestDto(
                program.getHandle(),
                program.getName(),
                program.getDescription(),
                program.getEngagementType(),
                program.getVisibility(),
                program.getPolicy(),
                program.getProofOfConceptRequirements(),
                program.getRulesOfEngagement(),
                program.getExclusions(),
                program.getOffersBounties(),
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                List.of(ProgramAssetRequestDto.builder()
                        .id(asset.getId())
                        .assetType(asset.getAssetType())
                        .identifier(asset.getIdentifier())
                        .description(asset.getDescription())
                        .isInScope(asset.getIsInScope())
                        .maxSeverity(asset.getMaxSeverity())
                        .build()),
                List.of()
        );

        service(new ProgramMapper()).updateProgram(program.getId(), request);

        assertEquals(ProgramState.ACTIVE, program.getState());
        assertEquals(SubmissionState.APPROVED, program.getSubmissionState());
        assertEquals(Visibility.PUBLIC, program.getVisibility());
        verify(programUpdateRepository, never()).save(any());
        verify(programRepository, never()).flush();
        verifyNoInteractions(followNotificationService);
    }

    @Test
    void approvedProgramCanBecomePublicWithoutAnotherReview() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PRIVATE);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        ProgramUpdateRequestDto request = new ProgramUpdateRequestDto(
                null,
                null,
                null,
                null,
                Visibility.PUBLIC,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        doAnswer(invocation -> {
            Program target = invocation.getArgument(1);
            target.setVisibility(request.visibility());
            return null;
        }).when(programMapper).updateEntity(eq(request), eq(program));

        service().updateProgram(program.getId(), request);

        assertEquals(SubmissionState.APPROVED, program.getSubmissionState());
        assertEquals(ProgramState.ACTIVE, program.getState());
        assertEquals(Visibility.PUBLIC, program.getVisibility());
        assertNotNull(program.getPublishedAt());
        verify(followNotificationService).notifyFollowers(
                FollowType.ORGANIZATION,
                organization.getId(),
                ownerId,
                "New program published",
                program.getName(),
                NotificationType.PROGRAM,
                program.getId(),
                "program-published:" + program.getId()
        );

        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(
                "Program visibility changed to public",
                updateCaptor.getValue().getChangeSummary()
        );
    }

    @Test
    void adminRejectionReturnsProgramToPrivateDraft() {
        UUID adminId = UUID.randomUUID();
        Program program = validProgram(UUID.randomUUID());
        program.setState(ProgramState.ACTIVE);
        program.setVisibility(Visibility.PUBLIC);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        authenticate(adminId, "ADMIN");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        service().rejectProgram(program.getId(), "Scope is incomplete");

        assertEquals(
                SubmissionState.REJECTED,
                program.getSubmissionState()
        );
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
    }

    @Test
    void approvedProgramCanBeLaunchedByOwner() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setSubmissionState(SubmissionState.APPROVED);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().publishProgram(program.getId());

        assertEquals(ProgramState.ACTIVE, program.getState());
    }

    @Test
    void approvedPausedProgramCanBeResumedByOwner() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.PAUSED);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().resumeProgram(program.getId());

        assertEquals(ProgramState.ACTIVE, program.getState());
        assertEquals(SubmissionState.APPROVED, program.getSubmissionState());
        assertEquals(Visibility.PUBLIC, program.getVisibility());

        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(
                "Program resumed",
                updateCaptor.getValue().getChangeSummary()
        );
    }

    @Test
    void ownerCanSoftDeletePublishedProgram() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().deleteProgram(program.getId());

        assertEquals(ProgramState.CLOSED, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
        assertNotNull(program.getDeletedAt());
        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(
                "Program deleted",
                updateCaptor.getValue().getChangeSummary()
        );
        verifyNoInteractions(followNotificationService);
    }

    @Test
    void deletedProgramCannotBeManagedAgain() {
        Program program = validProgram(UUID.randomUUID());
        program.setDeletedAt(LocalDateTime.now());
        authenticate(UUID.randomUUID(), "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().deleteProgram(program.getId())
        );

        verifyNoInteractions(organizationRepository);
        verifyNoInteractions(programUpdateRepository);
    }

    @Test
    void adminCanRemoveProgramWithoutBelongingToTheOrganization() {
        UUID adminId = UUID.randomUUID();
        Organization organization =
                activeOwnedOrganization(UUID.randomUUID());
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(adminId, "ADMIN");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().removeProgramByAdmin(program.getId());

        assertEquals(ProgramState.CLOSED, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
        assertEquals(SubmissionState.REJECTED, program.getSubmissionState());
        assertNotNull(program.getDeletedAt());
        verifyNoInteractions(organizationMemberRepository);
        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(
                "Program removed by admin",
                updateCaptor.getValue().getChangeSummary()
        );
        assertEquals(adminId, updateCaptor.getValue().getChangedBy());
        verify(eventPublisher).publishEvent(any(Object.class));
        verifyNoInteractions(followNotificationService);
    }

    @Test
    void onlyAdminCanRemoveAProgramOnBehalfOfModeration() {
        Program program = validProgram(UUID.randomUUID());
        authenticate(UUID.randomUUID(), "COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().removeProgramByAdmin(program.getId())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(programRepository);
        verifyNoInteractions(programUpdateRepository);
    }

    @Test
    void removingAnAlreadyRemovedProgramChangesNothing() {
        Program program = validProgram(UUID.randomUUID());
        LocalDateTime removedAt = LocalDateTime.now().minusDays(1);
        program.setDeletedAt(removedAt);
        authenticate(UUID.randomUUID(), "ADMIN");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        service().removeProgramByAdmin(program.getId());

        assertEquals(removedAt, program.getDeletedAt());
        verifyNoInteractions(programUpdateRepository);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    void restoringAnAdminRemovedProgramSendsItBackThroughReview() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        authenticate(UUID.randomUUID(), "ADMIN");
        service().removeProgramByAdmin(program.getId());

        authenticate(ownerId, "COMPANY");
        service().restoreProgram(program.getId());

        assertNull(program.getDeletedAt());
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
        assertEquals(SubmissionState.REJECTED, program.getSubmissionState());
    }

    @Test
    void restoreBringsBackAPrivateDraftInsteadOfRepublishing() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.CLOSED);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PRIVATE);
        program.setDeletedAt(LocalDateTime.now());
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().restoreProgram(program.getId());

        assertNull(program.getDeletedAt());
        assertEquals(ProgramState.DRAFT, program.getState());
        assertEquals(Visibility.PRIVATE, program.getVisibility());
        assertEquals(SubmissionState.APPROVED, program.getSubmissionState());
        ArgumentCaptor<ProgramUpdate> updateCaptor =
                ArgumentCaptor.forClass(ProgramUpdate.class);
        verify(programUpdateRepository).save(updateCaptor.capture());
        assertEquals(
                "Program restored as a private draft",
                updateCaptor.getValue().getChangeSummary()
        );
        verifyNoInteractions(followNotificationService);
    }

    @Test
    void restoreKeepsAPendingProgramWaitingForReview() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.CLOSED);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setDeletedAt(LocalDateTime.now());
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().restoreProgram(program.getId());

        assertNull(program.getDeletedAt());
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
    }

    @Test
    void liveProgramCannotBeRestored() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().restoreProgram(program.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Program is not deleted", exception.getReason());
        assertEquals(ProgramState.ACTIVE, program.getState());
        assertEquals(Visibility.PUBLIC, program.getVisibility());
        verifyNoInteractions(programUpdateRepository);
    }

    @Test
    void outsiderCannotRestoreAnotherOrganizationsProgram() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setDeletedAt(LocalDateTime.now());
        authenticate(UUID.randomUUID(), "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        assertThrows(
                ResponseStatusException.class,
                () -> service().restoreProgram(program.getId())
        );

        assertNotNull(program.getDeletedAt());
        verifyNoInteractions(programUpdateRepository);
    }

    @Test
    void deletedProgramsListingIsScopedToTheOwnOrganization() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setDeletedAt(LocalDateTime.now());
        authenticate(ownerId, "COMPANY");
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(organization));
        when(programRepository.findAll(
                any(Specification.class),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(program)));

        service().getMyDeletedPrograms(PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.DESC, "deletedAt")
        ));

        verify(programMapper).toManagementSummaryDto(
                eq(program),
                eq(organization),
                any()
        );
    }

    @Test
    void activeProgramCannotBeResumed() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().resumeProgram(program.getId())
        );

        assertEquals(
                "Only paused programs can be resumed",
                exception.getReason()
        );
        assertEquals(ProgramState.ACTIVE, program.getState());
        verify(programUpdateRepository, never())
                .save(any(ProgramUpdate.class));
    }

    @Test
    void rejectedProgramCanBeResubmittedByOwner() {
        UUID ownerId = UUID.randomUUID();
        Organization organization = activeOwnedOrganization(ownerId);
        Program program = validProgram(organization.getId());
        program.setSubmissionState(SubmissionState.REJECTED);
        authenticate(ownerId, "COMPANY");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));

        service().submitProgram(program.getId());

        assertEquals(
                SubmissionState.PENDING_REVIEW,
                program.getSubmissionState()
        );
        assertEquals(Visibility.PRIVATE, program.getVisibility());
    }

    @Test
    void publicLookupDoesNotRevealPrivateProgram() {
        Program program = validProgram(UUID.randomUUID());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PRIVATE);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getPublicProgramById(program.getId())
        );
    }

    @Test
    void publicLookupDoesNotRevealUnapprovedPublicProgram() {
        Program program = validProgram(UUID.randomUUID());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setVisibility(Visibility.PUBLIC);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getPublicProgramById(program.getId())
        );
    }

    @Test
    void adminCanViewFullPendingProgramDetails() {
        Program program = validProgram(UUID.randomUUID());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setVisibility(Visibility.PUBLIC);
        authenticate(UUID.randomUUID(), "ADMIN");
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));

        var response = service(new ProgramMapper())
                .getProgramForAdmin(program.getId());

        assertEquals(program.getId(), response.id());
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                response.submissionState()
        );
        assertEquals(program.getPolicy(), response.policy());
        assertEquals(
                program.getProofOfConceptRequirements(),
                response.proofOfConceptRequirements()
        );
        assertEquals(1, response.assets().size());
    }

    @Test
    void nonAdminCannotViewAdminProgramDetails() {
        Program program = validProgram(UUID.randomUUID());
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        authenticate(UUID.randomUUID(), "COMPANY");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getProgramForAdmin(program.getId())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(programRepository);
    }

    @Test
    void publicListIncludesOrganizationNameAndOnlyInScopeAssets() {
        Organization organization = activeOwnedOrganization(UUID.randomUUID());
        organization.setName("Acme Corporation");
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        program.setViewCount(21);
        program.setPublishedAt(LocalDateTime.now());
        ProgramAsset inScope = program.getAssets().getFirst();
        ProgramAsset outOfScope = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://legacy.acme.test")
                .isInScope(false)
                .build();
        PageRequest pageable = PageRequest.of(0, 20);

        when(programRepository.searchPublicPrograms(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("publishedAt"),
                eq("DESC"),
                eq(pageable)
        ))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(organizationRepository.findAllById(Set.of(organization.getId())))
                .thenReturn(List.of(organization));
        when(programAssetRepository.findInScopeByProgramIds(
                Set.of(program.getId())
        )).thenReturn(List.of(inScope, outOfScope));
        IdCountProjection followerCount = org.mockito.Mockito.mock(
                IdCountProjection.class
        );
        IdCountProjection submissionCount = org.mockito.Mockito.mock(
                IdCountProjection.class
        );
        when(followerCount.getId()).thenReturn(program.getId());
        when(followerCount.getTotal()).thenReturn(8L);
        when(submissionCount.getId()).thenReturn(program.getId());
        when(submissionCount.getTotal()).thenReturn(13L);
        when(followRepository.countByFollowableIds(
                FollowType.PROGRAM,
                Set.of(program.getId())
        )).thenReturn(List.of(followerCount));
        when(reportRepository.countByProgramIds(Set.of(program.getId())))
                .thenReturn(List.of(submissionCount));

        ProgramSummaryResponseDto response = service(new ProgramMapper())
                .getPublicPrograms(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        pageable
                )
                .getContent()
                .getFirst();

        assertEquals("Acme Corporation", response.organizationName());
        assertEquals(1, response.inScopeAssets().size());
        assertEquals(21, response.viewCount());
        assertEquals(8, response.followerCount());
        assertEquals(13, response.totalSubmissions());
        assertEquals(
                "https://app.acme.test",
                response.inScopeAssets().getFirst().identifier()
        );
    }

    @Test
    void publicListNormalizesFiltersAndSupportsAggregateSorting() {
        UUID organizationId = UUID.randomUUID();
        BigDecimal minimumBounty = new BigDecimal("100.00");
        BigDecimal maximumBounty = new BigDecimal("1000.00");
        PageRequest requestedPage = PageRequest.of(
                1,
                25,
                Sort.by(Sort.Order.desc("followerCount"))
        );
        PageRequest databasePage = PageRequest.of(1, 25);
        when(programRepository.searchPublicPrograms(
                organizationId,
                "bounty",
                true,
                "%acme\\_100\\%%",
                minimumBounty,
                maximumBounty,
                "api",
                "critical",
                "technology",
                "cambodia",
                "followerCount",
                "DESC",
                databasePage
        )).thenReturn(Page.empty(databasePage));

        Page<ProgramSummaryResponseDto> response = service(
                new ProgramMapper()
        ).getPublicPrograms(
                organizationId,
                EngagementType.BOUNTY,
                true,
                " Acme_100% ",
                minimumBounty,
                maximumBounty,
                AssetType.API,
                Severity.CRITICAL,
                Industry.TECHNOLOGY,
                " Cambodia ",
                requestedPage
        );

        assertEquals(requestedPage, response.getPageable());
        verify(programRepository).searchPublicPrograms(
                organizationId,
                "bounty",
                true,
                "%acme\\_100\\%%",
                minimumBounty,
                maximumBounty,
                "api",
                "critical",
                "technology",
                "cambodia",
                "followerCount",
                "DESC",
                databasePage
        );
    }

    @Test
    void publicListRejectsAnInvertedBountyRange() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service(new ProgramMapper()).getPublicPrograms(
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("500.00"),
                        new BigDecimal("100.00"),
                        null,
                        null,
                        null,
                        null,
                        PageRequest.of(0, 20)
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(programRepository);
    }

    @Test
    void publicDetailIncludesAllAssetsAndProgramStatistics() {
        Organization organization = activeOwnedOrganization(UUID.randomUUID());
        organization.setName("Acme Corporation");
        Program program = validProgram(organization.getId());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setVisibility(Visibility.PUBLIC);
        ProgramAsset outOfScope = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://legacy.acme.test")
                .isInScope(false)
                .build();
        ProgramRepository.PublicProgramStatistics statistics =
                org.mockito.Mockito.mock(
                        ProgramRepository.PublicProgramStatistics.class
                );

        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(programAssetRepository.findByProgramIdOrderByCreatedAtAsc(
                program.getId()
        )).thenReturn(List.of(program.getAssets().getFirst(), outOfScope));
        when(programRepository.findPublicStatisticsByProgramId(
                program.getId()
        )).thenReturn(statistics);
        when(statistics.getTotalResearchers()).thenReturn(7L);
        when(statistics.getTotalSubmissions()).thenReturn(12L);
        when(followRepository.countByFollowableTypeAndFollowableId(
                FollowType.PROGRAM,
                program.getId()
        )).thenReturn(8L);

        var response = service(new ProgramMapper())
                .getPublicProgramById(program.getId());

        assertEquals(7, response.totalResearchers());
        assertEquals(12, response.totalSubmissions());
        assertEquals(8, response.followerCount());
        assertEquals(2, response.assets().size());
        assertTrue(response.assets().stream().anyMatch(asset ->
                Boolean.FALSE.equals(asset.isInScope())
        ));
    }

    @Test
    void incrementViewCountUsesGuardAndAtomicPublicUpdate() {
        UUID programId = UUID.randomUUID();
        when(viewCountGuard.shouldCount("program", programId))
                .thenReturn(true);
        when(programRepository.incrementPublicViewCount(
                programId,
                ProgramState.ACTIVE,
                SubmissionState.APPROVED,
                Visibility.PUBLIC
        )).thenReturn(1);
        when(programRepository.findPublicViewCountById(
                programId,
                ProgramState.ACTIVE,
                SubmissionState.APPROVED,
                Visibility.PUBLIC
        )).thenReturn(42L);

        var response = service().incrementViewCount(programId);

        assertEquals(programId, response.programId());
        assertEquals(42, response.viewCount());
    }

    @Test
    void repeatedProgramViewReturnsCountWithoutIncrementing() {
        UUID programId = UUID.randomUUID();
        when(viewCountGuard.shouldCount("program", programId))
                .thenReturn(false);
        when(programRepository.findPublicViewCountById(
                programId,
                ProgramState.ACTIVE,
                SubmissionState.APPROVED,
                Visibility.PUBLIC
        )).thenReturn(42L);

        var response = service().incrementViewCount(programId);

        assertEquals(42, response.viewCount());
        verify(programRepository, never()).incrementPublicViewCount(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void reviewListRejectsSwaggerPlaceholderSortBeforeRepositoryCall() {
        authenticate(UUID.randomUUID(), "ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getProgramsForReview(
                        SubmissionState.PENDING_REVIEW,
                        PageRequest.of(
                                0,
                                20,
                                Sort.by("[\"string\"]")
                        )
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(programRepository);
    }

    @Test
    void reviewListIncludesOrganizationDisplayInformation() {
        Organization organization = activeOwnedOrganization(UUID.randomUUID());
        organization.setName("Acme Corporation");
        organization.setSlug("acme-corporation");
        organization.setLogoUrl("https://acme.test/logo.png");
        organization.setWebsiteUrl("https://acme.test");
        Program program = validProgram(organization.getId());
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        PageRequest pageable = PageRequest.of(0, 20);
        authenticate(UUID.randomUUID(), "ADMIN");

        when(programRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Program>>any(),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(organizationRepository.findAllById(Set.of(organization.getId())))
                .thenReturn(List.of(organization));

        var response = service(new ProgramMapper())
                .getProgramsForReview(
                        SubmissionState.PENDING_REVIEW,
                        pageable
                )
                .getContent()
                .getFirst();

        assertEquals("Acme Corporation", response.organizationName());
        assertEquals("acme-corporation", response.organizationSlug());
        assertEquals(
                "https://acme.test/logo.png",
                response.organizationLogoUrl()
        );
        assertEquals(
                "https://acme.test",
                response.organizationWebsiteUrl()
        );
        assertEquals(
                OrganizationStatus.ACTIVE,
                response.organizationStatus()
        );
    }

    private ProgramServiceImpl service() {
        lenient().when(programUpdateRepository.save(any(ProgramUpdate.class)))
                .thenAnswer(invocation -> {
                    ProgramUpdate update = invocation.getArgument(0);
                    if (update.getId() == null) {
                        update.setId(UUID.randomUUID());
                    }
                    return update;
                });
        return service(programMapper);
    }

    private ProgramServiceImpl service(ProgramMapper mapper) {
        return new ProgramServiceImpl(
                programRepository,
                programAssetRepository,
                programUpdateRepository,
                mapper,
                organizationRepository,
                new OrganizationAuthorizationService(
                        organizationRepository,
                        organizationMemberRepository
                ),
                followNotificationService,
                followRepository,
                reportRepository,
                viewCountGuard,
                companyIdentityService,
                eventPublisher
        );
    }

    private Program validProgram(UUID organizationId) {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(organizationId);
        program.setHandle("acme-security");
        program.setName("Acme Security Program");
        program.setPolicy("Test only the assets listed as in scope.");
        program.setProofOfConceptRequirements(new ProgramGuidelinesDto(
                "Include reproducible steps and supporting evidence.",
                List.of("Attach an HTTP request trace")
        ));
        program.setRulesOfEngagement(new ProgramGuidelinesDto(
                "Follow these rules during testing.",
                List.of("Test only assets listed as in scope")
        ));
        program.setExclusions(new ProgramGuidelinesDto(
                "The following findings are excluded.",
                List.of("Self-XSS without demonstrated impact")
        ));
        program.setEngagementType(EngagementType.BOUNTY);
        program.setVisibility(Visibility.PRIVATE);
        program.setOffersBounties(false);

        ProgramAsset asset = ProgramAsset.builder()
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://app.acme.test")
                .isInScope(true)
                .build();
        program.getAssets().add(asset);
        return program;
    }

    private Organization activeOwnedOrganization(UUID ownerId) {
        UserProfile owner = new UserProfile();
        owner.setId(ownerId);

        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setOwner(owner);
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    private void authenticate(UUID userId, String role) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
