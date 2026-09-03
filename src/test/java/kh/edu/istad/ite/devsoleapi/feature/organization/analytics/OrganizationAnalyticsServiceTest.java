package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What this layer is for: deciding who may look, before anything is read.
 *
 * <p>The figures themselves come from a cache, so every refusal has to happen
 * out here — a check inside the cached call would stop running the moment it
 * started serving hits.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationAnalyticsServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    @Mock
    private OrganizationAuthorizationService organizationAuthorization;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private OrganizationAnalyticsCache analyticsCache;

    @BeforeEach
    void authenticate() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readingTheDashboardNeedsViewProgramsAtThatOrganization() {
        Organization organization = organization();
        OrganizationAnalyticsResponse expected = emptyAnalytics();
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                ORGANIZATION_ID,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization);
        when(analyticsCache.load(
                ORGANIZATION_ID,
                "Acme Corp",
                AnalyticsTimeRange.LAST_30_DAYS,
                null
        )).thenReturn(expected);

        assertSame(
                expected,
                service().getAnalytics(ORGANIZATION_ID, "30d", null)
        );
    }

    /**
     * The refusal has to land before the cache is touched. If it did not, the
     * first person entitled to the figures would warm an entry that everybody
     * else then got for free.
     */
    @Test
    void nothingIsReadWhenTheCallerIsNotOnTheTeam() {
        when(organizationAuthorization.findAccessibleOrganization(
                any(),
                any(),
                any()
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You are not a member of an active organization"
        ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getAnalytics(ORGANIZATION_ID, "6m", null)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(analyticsCache, programRepository);
    }

    @Test
    void anAbsentRangeAsksTheCacheForTheDefaultWindow() {
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                null,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization());

        service().getAnalytics(null, null, null);

        verify(analyticsCache).load(
                ORGANIZATION_ID,
                "Acme Corp",
                AnalyticsTimeRange.DEFAULT,
                null
        );
    }

    @Test
    void anUnknownRangeIsRefusedBeforeAnythingIsRead() {
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                ORGANIZATION_ID,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().getAnalytics(ORGANIZATION_ID, "7d", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(analyticsCache);
    }

    @Test
    void aProgramOfTheOrganizationNarrowsTheDashboard() {
        UUID programId = UUID.randomUUID();
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                ORGANIZATION_ID,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization());
        when(programRepository.findById(programId))
                .thenReturn(Optional.of(program(programId, ORGANIZATION_ID)));

        service().getAnalytics(ORGANIZATION_ID, "6m", programId);

        verify(analyticsCache).load(
                ORGANIZATION_ID,
                "Acme Corp",
                AnalyticsTimeRange.LAST_6_MONTHS,
                programId
        );
    }

    /**
     * The scope clause would answer this with zeros across the board, which
     * reads as "this program has had a quiet six months" rather than "that is
     * not your program".
     */
    @Test
    void anotherCompanysProgramIsNotFound() {
        UUID programId = UUID.randomUUID();
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                ORGANIZATION_ID,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization());
        when(programRepository.findById(programId)).thenReturn(
                Optional.of(program(programId, UUID.randomUUID()))
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getAnalytics(ORGANIZATION_ID, "6m", programId)
        );
        verifyNoInteractions(analyticsCache);
    }

    @Test
    void aDeletedProgramIsNotFoundEither() {
        UUID programId = UUID.randomUUID();
        Program deleted = program(programId, ORGANIZATION_ID);
        deleted.setDeletedAt(LocalDateTime.now());
        when(organizationAuthorization.findAccessibleOrganization(
                USER_ID,
                ORGANIZATION_ID,
                OrganizationPermission.VIEW_PROGRAMS
        )).thenReturn(organization());
        when(programRepository.findById(programId))
                .thenReturn(Optional.of(deleted));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getAnalytics(ORGANIZATION_ID, "6m", programId)
        );
        verifyNoInteractions(analyticsCache);
    }

    private OrganizationAnalyticsResponse emptyAnalytics() {
        return new OrganizationAnalyticsResponse(
                ORGANIZATION_ID,
                "Acme Corp",
                "30d",
                null,
                Instant.now(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private OrganizationAnalyticsService service() {
        return new OrganizationAnalyticsService(
                organizationAuthorization,
                programRepository,
                analyticsCache
        );
    }

    private Organization organization() {
        UserProfile owner = new UserProfile();
        owner.setId(UUID.randomUUID());

        Organization organization = new Organization();
        organization.setId(ORGANIZATION_ID);
        organization.setName("Acme Corp");
        organization.setOwner(owner);
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organization;
    }

    private Program program(UUID programId, UUID organizationId) {
        return Program.builder()
                .id(programId)
                .organizationId(organizationId)
                .name("Acme VDP")
                .handle("acme-vdp")
                .build();
    }
}
