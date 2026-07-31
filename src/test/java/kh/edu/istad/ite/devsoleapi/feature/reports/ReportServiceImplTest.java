package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.*;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportRewardRepository reportRewardRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private WeaknessRepository weaknessRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private FollowNotificationService followNotificationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hackerCreatesPrivateReportForInScopeAsset() {
        UUID hackerId = UUID.randomUUID();
        UserProfile hacker = user(hackerId);
        Program program = activeApprovedProgram();
        ProgramAsset asset = program.getAssets().getFirst();
        authenticate(hackerId, "USER");

        when(userProfileRepository.findById(hackerId))
                .thenReturn(Optional.of(hacker));
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().create(
                program.getId(),
                new CreateReportRequest(
                        "Broken access control",
                        "A user can read another account.",
                        "Private account data is exposed.",
                        Severity.HIGH,
                        null,
                        asset.getId()
                )
        );

        ArgumentCaptor<Report> reportCaptor =
                ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());
        Report report = reportCaptor.getValue();
        assertSame(program, report.getProgram());
        assertSame(hacker, report.getReporter());
        assertSame(asset, report.getAsset());
        assertEquals(Severity.HIGH, report.getReportedSeverity());
        assertNull(report.getTriageSeverity());
        assertNull(report.getSeverity());
        assertEquals(ReportState.NEW, report.getState());
    }

    @Test
    void matchingCompanyTriageSetsFinalSeverityWithoutDispute() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.HIGH,
                        ReportState.VALID_CONFIRMED,
                        null
                )
        );

        assertEquals(Severity.HIGH, report.getTriageSeverity());
        assertEquals(Severity.HIGH, report.getSeverity());
        assertEquals(ReportState.VALID_CONFIRMED, report.getState());
        verify(disputeRepository, never()).save(any(Dispute.class));
    }

    @Test
    void mismatchingCompanyTriageLeavesFinalBlankAndOpensDispute() {
        UUID ownerId = UUID.randomUUID();
        Report report = newReport(Severity.CRITICAL);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                ownerId
        );
        authenticate(ownerId, "COMPANY");

        stubCompanyOwnedReport(report, organization, ownerId);
        when(disputeRepository
                .findFirstByReportIdAndStatusInOrderByCreatedAtDesc(
                        eq(report.getId()),
                        anyCollection()
                ))
                .thenReturn(Optional.empty());
        when(reportRepository.saveAndFlush(report)).thenReturn(report);
        when(disputeRepository.save(any(Dispute.class)))
                .thenAnswer(invocation -> {
                    Dispute dispute = invocation.getArgument(0);
                    dispute.setId(UUID.randomUUID());
                    return dispute;
                });

        service().triage(
                report.getId(),
                new TriageReportRequest(
                        Severity.MEDIUM,
                        ReportState.VALID_CONFIRMED,
                        null
                )
        );

        assertEquals(Severity.MEDIUM, report.getTriageSeverity());
        assertNull(report.getSeverity());
        verify(disputeRepository).save(any(Dispute.class));
        assertEquals(1, report.getDisputes().size());
        assertSame(
                report.getReporter(),
                report.getDisputes().getFirst().getRaisedBy()
        );
    }

    @Test
    void unrelatedHackerCannotDiscoverPrivateReport() {
        UUID hackerId = UUID.randomUUID();
        Report report = newReport(Severity.LOW);
        authenticate(hackerId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().findById(report.getId())
        );
    }

    @Test
    void reporterCanDiscussButCannotSeeOrCreateInternalNotes() {
        Report report = newReport(Severity.LOW);
        UUID reporterId = report.getReporter().getId();
        authenticate(reporterId, "USER");
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertFalse(access.canViewInternal());
        assertTrue(access.canComment());
        assertFalse(access.canCreateInternal());
    }

    @Test
    void organizationViewerSeesInternalNotesButCannotWriteThem() {
        UUID viewerId = UUID.randomUUID();
        Report report = newReport(Severity.MEDIUM);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember viewer = new OrganizationMember();
        viewer.setOrganization(organization);
        viewer.setUser(user(viewerId));
        viewer.setStatus(MembershipStatus.ACTIVE);
        viewer.setPermissions(Set.of(
                OrganizationPermission.VIEW_REPORTS
        ));
        authenticate(viewerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        viewerId
                ))
                .thenReturn(Optional.of(viewer));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertTrue(access.canViewInternal());
        assertFalse(access.canComment());
        assertFalse(access.canCreateInternal());
    }

    @Test
    void organizationTriagerCanParticipateAndCreateInternalNotes() {
        UUID triagerId = UUID.randomUUID();
        Report report = newReport(Severity.HIGH);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember triager = new OrganizationMember();
        triager.setOrganization(organization);
        triager.setUser(user(triagerId));
        triager.setStatus(MembershipStatus.ACTIVE);
        triager.setPermissions(Set.of(
                OrganizationPermission.VIEW_REPORTS,
                OrganizationPermission.TRIAGE_REPORTS
        ));
        authenticate(triagerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        triagerId
                ))
                .thenReturn(Optional.of(triager));

        ReportDiscussionAccess access =
                service().requireDiscussionAccess(report.getId());

        assertTrue(access.canViewInternal());
        assertTrue(access.canComment());
        assertTrue(access.canCreateInternal());
    }

    @Test
    void organizationViewerCanReadButCannotTriage() {
        UUID viewerId = UUID.randomUUID();
        Report report = newReport(Severity.MEDIUM);
        Organization organization = activeOrganization(
                report.getProgram().getOrganizationId(),
                UUID.randomUUID()
        );
        OrganizationMember viewer = new OrganizationMember();
        viewer.setOrganization(organization);
        viewer.setUser(user(viewerId));
        viewer.setStatus(MembershipStatus.ACTIVE);
        viewer.setRole(OrgRole.VIEWER);
        authenticate(viewerId, "COMPANY");

        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationMemberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        viewerId
                ))
                .thenReturn(Optional.of(viewer));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().triage(
                        report.getId(),
                        new TriageReportRequest(
                                Severity.MEDIUM,
                                ReportState.VALID_CONFIRMED,
                                null
                        )
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    private void stubCompanyOwnedReport(
            Report report,
            Organization organization,
            UUID ownerId
    ) {
        when(reportRepository.findById(report.getId()))
                .thenReturn(Optional.of(report));
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userProfileRepository.findById(ownerId))
                .thenReturn(Optional.of(organization.getOwner()));
    }

    private ReportServiceImpl service() {
        return new ReportServiceImpl(
                reportRepository,
                reportRewardRepository,
                disputeRepository,
                weaknessRepository,
                programRepository,
                userProfileRepository,
                new OrganizationAuthorizationService(
                        organizationRepository,
                        organizationMemberRepository
                ),
                reportMapper,
                followNotificationService
        );
    }

    private Report newReport(Severity reportedSeverity) {
        Program program = activeApprovedProgram();
        return Report.builder()
                .id(UUID.randomUUID())
                .program(program)
                .reporter(user(UUID.randomUUID()))
                .title("Security issue")
                .vulnerabilityInformation("Steps to reproduce")
                .reportedSeverity(reportedSeverity)
                .asset(program.getAssets().getFirst())
                .state(ReportState.NEW)
                .build();
    }

    private Program activeApprovedProgram() {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED);
        program.setOffersBounties(true);

        ProgramAsset asset = ProgramAsset.builder()
                .id(UUID.randomUUID())
                .program(program)
                .assetType(AssetType.URL)
                .identifier("https://app.example.test")
                .isInScope(true)
                .maxSeverity(Severity.CRITICAL)
                .build();
        program.getAssets().add(asset);
        return program;
    }

    private Organization activeOrganization(
            UUID organizationId,
            UUID ownerId
    ) {
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setOwner(user(ownerId));
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    private UserProfile user(UUID userId) {
        UserProfile user = new UserProfile();
        user.setId(userId);
        return user;
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
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_" + role
                        ))
                )
        );
    }
}
