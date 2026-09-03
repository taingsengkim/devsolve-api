package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The dashboard read, entered the way a request enters it: with no transaction
 * already open.
 *
 * <p>This is the gap that let a {@code LazyInitializationException} reach
 * production. Resolving the caller's organization walks a member's lazily
 * loaded permissions, and the service unit test mocks the authorization service
 * away, so nothing there ever touches a real collection. Nothing here is
 * {@code @Transactional} on purpose — annotating the test would open the very
 * session whose absence is the bug.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create",
        // Without Redis the @Cacheable is a plain call, so every run exercises
        // the real path rather than a warmed entry from the run before it.
        "app.redis.enabled=false"
})
class OrganizationAnalyticsTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private OrganizationAnalyticsService analyticsService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMemberRepository memberRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * A member, not the owner. Ownership short-circuits the permission check
     * without reading the collection, so an owner would pass even with the bug.
     */
    @Test
    void aMemberCanReadTheDashboardOutsideATransaction() {
        Organization organization = persistedOrganization();
        UserProfile member = persistedProfile();
        persistedMembership(organization, member);
        authenticate(member.getId());

        OrganizationAnalyticsResponse analytics =
                analyticsService.getAnalytics(null, "6m", null);

        assertNotNull(analytics);
        assertEquals(organization.getId(), analytics.organizationId());
        assertEquals("Acme Security", analytics.organizationName());
        assertEquals("6m", analytics.timeRange());
        assertEquals(0L, analytics.kpiSummary().totalReports().value());
    }

    @Test
    void theOwnerCanReadItToo() {
        Organization organization = persistedOrganization();
        authenticate(organization.getOwner().getId());

        assertEquals(
                organization.getId(),
                analyticsService.getAnalytics(null, "all", null)
                        .organizationId()
        );
    }

    /** Every range runs the same walk over the same lazy collection. */
    @Test
    void everyTimeRangeIsReadableOutsideATransaction() {
        Organization organization = persistedOrganization();
        UserProfile member = persistedProfile();
        persistedMembership(organization, member);
        authenticate(member.getId());

        for (AnalyticsTimeRange range : AnalyticsTimeRange.values()) {
            assertNotNull(
                    analyticsService.getAnalytics(
                            organization.getId(),
                            range.wireValue(),
                            null
                    ),
                    range.wireValue()
            );
        }
    }

    private Organization persistedOrganization() {
        UserProfile owner = persistedProfile();
        Organization organization = new Organization();
        organization.setName("Acme Security");
        organization.setSlug("acme-security-" + UUID.randomUUID());
        organization.setOwner(owner);
        organization.setOwnerJobTitle("Security Manager");
        organization.setCompanySize("11-50");
        organization.setCountry("Cambodia");
        organization.setJoiningReason("Run a bug bounty programme.");
        organization.setIndustry(Industry.TECHNOLOGY);
        organization.setStatus(OrganizationStatus.ACTIVE);
        return organizationRepository.saveAndFlush(organization);
    }

    private void persistedMembership(
            Organization organization,
            UserProfile user
    ) {
        OrganizationMember membership = new OrganizationMember();
        membership.setOrganization(organization);
        membership.setUser(user);
        membership.setRole(OrgRole.VIEWER);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setPermissions(
                OrganizationPermission.defaultsFor(OrgRole.VIEWER)
        );
        memberRepository.saveAndFlush(membership);
    }

    private UserProfile persistedProfile() {
        String handle = "user" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(handle + "@example.test");
        profile.setUsername(handle);
        profile.setFullName("Test Person");
        profile.setStatus(UserStatus.ACTIVE);
        return userProfileRepository.saveAndFlush(profile);
    }

    private void authenticate(UUID userId) {
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
                        List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
                )
        );
    }
}
