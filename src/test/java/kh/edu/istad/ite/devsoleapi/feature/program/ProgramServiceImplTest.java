package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        service().updateProgram(
                program.getId(),
                new ProgramUpdateRequestDto(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Provide a working exploit and an HTTP request trace.",
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
        ProgramAsset inScope = program.getAssets().getFirst();
        ProgramAsset outOfScope = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://legacy.acme.test")
                .isInScope(false)
                .build();
        PageRequest pageable = PageRequest.of(0, 20);

        when(programRepository.findAll(
                org.mockito.ArgumentMatchers
                        .<Specification<Program>>any(),
                eq(pageable)
        ))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(organizationRepository.findAllById(Set.of(organization.getId())))
                .thenReturn(List.of(organization));
        when(programAssetRepository.findInScopeByProgramIds(
                Set.of(program.getId())
        )).thenReturn(List.of(inScope, outOfScope));

        ProgramSummaryResponseDto response = service(new ProgramMapper())
                .getPublicPrograms(null, null, null, pageable)
                .getContent()
                .getFirst();

        assertEquals("Acme Corporation", response.organizationName());
        assertEquals(1, response.inScopeAssets().size());
        assertEquals(
                "https://app.acme.test",
                response.inScopeAssets().getFirst().identifier()
        );
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

        var response = service(new ProgramMapper())
                .getPublicProgramById(program.getId());

        assertEquals(7, response.totalResearchers());
        assertEquals(12, response.totalSubmissions());
        assertEquals(2, response.assets().size());
        assertTrue(response.assets().stream().anyMatch(asset ->
                Boolean.FALSE.equals(asset.isInScope())
        ));
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
                followNotificationService
        );
    }

    private Program validProgram(UUID organizationId) {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(organizationId);
        program.setHandle("acme-security");
        program.setName("Acme Security Program");
        program.setPolicy("Test only the assets listed as in scope.");
        program.setProofOfConceptRequirements(
                "Include reproducible steps and supporting evidence."
        );
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
