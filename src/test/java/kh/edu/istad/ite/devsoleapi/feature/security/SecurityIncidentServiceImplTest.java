package kh.edu.istad.ite.devsoleapi.feature.security;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.AttachmentScanContext;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityIncidentServiceImplTest {

    private final SecurityIncidentRepository incidentRepository =
            mock(SecurityIncidentRepository.class);
    private final UserProfileRepository userProfileRepository =
            mock(UserProfileRepository.class);
    private final OrganizationRepository organizationRepository =
            mock(OrganizationRepository.class);

    private final OrganizationAuthorizationService organizationAuthorization =
            mock(OrganizationAuthorizationService.class);

    private final SecurityIncidentServiceImpl service =
            new SecurityIncidentServiceImpl(
                    incidentRepository,
                    userProfileRepository,
                    organizationRepository,
                    organizationAuthorization
            );

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsEverythingNeededToActOnTheIncident() {
        UUID uploaderId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();

        when(userProfileRepository.findById(uploaderId))
                .thenReturn(Optional.of(profile("hunter_x", "hx@example.test")));
        when(organizationRepository.findById(organizationId))
                .thenReturn(Optional.of(organization("Target Corp")));

        service.record(
                uploaderId,
                attachment(),
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                malicious(),
                new AttachmentScanContext(
                        organizationId,
                        reportId,
                        NotificationType.SECURITY,
                        "a report"
                )
        );

        SecurityIncident saved = captureSaved();

        assertEquals(uploaderId, saved.getUploaderUserId());
        assertEquals("hunter_x", saved.getUploaderUsername());
        assertEquals("hx@example.test", saved.getUploaderEmail());
        assertEquals(organizationId, saved.getOrganizationId());
        assertEquals("Target Corp", saved.getOrganizationName());
        assertEquals(reportId, saved.getReportId());
        assertEquals("payload.txt", saved.getFilename());
        assertEquals(7, saved.getFileSizeBytes());
        assertEquals(
                VirusTotalScanResponse.Verdict.MALICIOUS,
                saved.getVerdict()
        );
        assertEquals("analysis-id", saved.getAnalysisId());
        assertTrue(saved.getSha256Hash().matches("[0-9a-f]{64}"));
        assertNotNull(saved.getBlockedAt());
    }

    /**
     * VirusTotal omits the categories no engine returned, so the total is the
     * sum of what came back rather than a field of its own.
     */
    @Test
    void totalEngineCountIsTheSumOfEveryCategoryReturned() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("malicious", 12);
        stats.put("suspicious", 3);
        stats.put("undetected", 57);

        service.record(
                UUID.randomUUID(),
                attachment(),
                "abc",
                new VirusTotalScanResponse(
                        "analysis-id",
                        "completed",
                        VirusTotalScanResponse.Verdict.MALICIOUS,
                        stats
                ),
                AttachmentScanContext.NONE
        );

        SecurityIncident saved = captureSaved();

        assertEquals(12, saved.getMaliciousEnginesCount());
        assertEquals(3, saved.getSuspiciousEnginesCount());
        assertEquals(72, saved.getTotalEnginesCount());
    }

    /**
     * The incident is the point. A deleted uploader or an upload that belonged
     * to no company still has to leave a row rather than throwing on the way
     * to a refusal that has already been decided.
     */
    @Test
    void recordsWithoutAnUploaderProfileOrAnOrganization() {
        UUID uploaderId = UUID.randomUUID();
        when(userProfileRepository.findById(uploaderId))
                .thenReturn(Optional.empty());

        service.record(
                uploaderId,
                attachment(),
                "abc",
                malicious(),
                AttachmentScanContext.NONE
        );

        SecurityIncident saved = captureSaved();

        assertEquals(uploaderId, saved.getUploaderUserId());
        assertNull(saved.getUploaderUsername());
        assertNull(saved.getOrganizationId());
        assertNull(saved.getOrganizationName());
        assertNull(saved.getReportId());
    }

    @Test
    void anUnauthenticatedUploaderIsStillRecorded() {
        service.record(
                null,
                attachment(),
                "abc",
                malicious(),
                AttachmentScanContext.NONE
        );

        assertNull(captureSaved().getUploaderUserId());
    }

    // ------------------------------------------------------ authorisation

    /**
     * The check lives on the service rather than the controller because
     * answering it walks a member's lazily-loaded permissions, and a
     * controller method runs with no Hibernate session: asking there threw a
     * LazyInitializationException — a 500 — instead of refusing or allowing.
     * Keeping it behind the service's transaction is what makes it answerable
     * at all.
     */
    @Test
    void anOrganizationSearchIsRefusedWithoutTriagePermission() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(organizationAuthorization.findUserIdsWithPermission(
                organizationId,
                OrganizationPermission.TRIAGE_REPORTS
        )).thenReturn(Set.of(UUID.randomUUID()));

        ResponseStatusException refused = assertThrows(
                ResponseStatusException.class,
                () -> service.searchForOrganization(
                        organizationId,
                        userId,
                        null,
                        null,
                        PageRequest.of(0, 20)
                )
        );

        assertEquals(403, refused.getStatusCode().value());
        verify(incidentRepository, never()).findAll(
                ArgumentMatchers.<Specification<SecurityIncident>>any(),
                any(PageRequest.class)
        );
    }

    @Test
    void aTriagerReachesTheirOwnOrganizationsIncidents() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(organizationAuthorization.findUserIdsWithPermission(
                organizationId,
                OrganizationPermission.TRIAGE_REPORTS
        )).thenReturn(Set.of(userId));
        when(incidentRepository.findAll(
                ArgumentMatchers.<Specification<SecurityIncident>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.searchForOrganization(
                organizationId,
                userId,
                null,
                null,
                PageRequest.of(0, 20)
        );

        verify(incidentRepository).findAll(
                ArgumentMatchers.<Specification<SecurityIncident>>any(),
                any(Pageable.class)
        );
    }

    /**
     * Platform admins reach every company's incidents without a membership
     * row, the same exemption recognitions make.
     */
    @Test
    void aPlatformAdminNeedsNoMembership() {
        UUID organizationId = UUID.randomUUID();
        authenticateAsPlatformAdmin();

        when(incidentRepository.findAll(
                ArgumentMatchers.<Specification<SecurityIncident>>any(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.searchForOrganization(
                organizationId,
                UUID.randomUUID(),
                null,
                null,
                PageRequest.of(0, 20)
        );

        verify(organizationAuthorization, never())
                .findUserIdsWithPermission(any(), any());
    }

    private void authenticateAsPlatformAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "admin",
                        "n/a",
                        "ROLE_ADMIN"
                )
        );
    }

    // -------------------------------------------------------------- paging

    @Test
    void anUnknownSortIsRejectedRatherThanReachingTheQuery() {
        ResponseStatusException rejected = assertThrows(
                ResponseStatusException.class,
                () -> SecurityIncidentPaging.resolve(
                        PageRequest.of(0, 20, Sort.by("uploaderEmail"))
                )
        );

        assertEquals(400, rejected.getStatusCode().value());
        assertTrue(rejected.getReason().contains("blockedAt"));
    }

    @Test
    void anOversizedPageIsCappedRatherThanRefused() {
        assertEquals(
                SecurityIncidentPaging.MAX_PAGE_SIZE,
                SecurityIncidentPaging.resolve(PageRequest.of(0, 5000))
                        .getPageSize()
        );
    }

    @Test
    void anUnsortedRequestIsNewestFirst() {
        Sort sort = SecurityIncidentPaging
                .resolve(PageRequest.of(0, 20))
                .getSort();

        assertEquals(
                Sort.by(Sort.Direction.DESC, "blockedAt"),
                sort
        );
    }

    // ------------------------------------------------------------ fixtures

    private SecurityIncident captureSaved() {
        ArgumentCaptor<SecurityIncident> captor =
                ArgumentCaptor.forClass(SecurityIncident.class);
        verify(incidentRepository).save(captor.capture());
        return captor.getValue();
    }

    private AttachmentValidator.ValidatedAttachment attachment() {
        return new AttachmentValidator.ValidatedAttachment(
                "payload.txt",
                "txt",
                "text/plain",
                "payload".getBytes(StandardCharsets.UTF_8)
        );
    }

    private VirusTotalScanResponse malicious() {
        return new VirusTotalScanResponse(
                "analysis-id",
                "completed",
                VirusTotalScanResponse.Verdict.MALICIOUS,
                Map.of("malicious", 58, "undetected", 12)
        );
    }

    private UserProfile profile(String username, String email) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setUsername(username);
        profile.setEmail(email);
        return profile;
    }

    private Organization organization(String name) {
        Organization organization = new Organization();
        organization.setName(name);
        return organization;
    }
}
