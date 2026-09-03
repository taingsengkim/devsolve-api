package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Who may read an organization's analytics, and over what.
 *
 * <p>Everything expensive happens in {@link OrganizationAnalyticsCache}. This
 * layer exists to keep the permission check outside that cache: a
 * {@code @Cacheable} method that authorized its own caller would stop
 * authorizing anybody the moment it started serving hits.
 *
 * <p>Transactional even though it reads nothing itself. Resolving the caller's
 * organization walks a member's lazily loaded permissions, which needs a
 * session open — the cache's own transaction starts too late for that, and
 * every other caller of
 * {@link OrganizationAuthorizationService#findAccessibleOrganization} is
 * transactional for the same reason.
 */
@Service
@RequiredArgsConstructor
public class OrganizationAnalyticsService {

    /**
     * The dashboard is a view of the organization's programs, so the
     * permission that gates seeing them gates the figures about them. A VIEWER
     * holds it by default.
     */
    private static final OrganizationPermission REQUIRED_PERMISSION =
            OrganizationPermission.VIEW_PROGRAMS;

    private final OrganizationAuthorizationService organizationAuthorization;
    private final ProgramRepository programRepository;
    private final OrganizationAnalyticsCache analyticsCache;

    /**
     * @param organizationId which organization. Null asks for the only one the
     *                       caller belongs to, and is answered with 409 naming
     *                       the candidates when there is a choice.
     * @param timeRange      the {@code timeRange} query parameter, or null for
     *                       the default window
     * @param programId      one program to narrow to, or null for all of them.
     *                       Refused with 404 when it is not a live program of
     *                       this organization, rather than answering with an
     *                       empty dashboard.
     */
    @Transactional(readOnly = true)
    public OrganizationAnalyticsResponse getAnalytics(
            UUID organizationId,
            String timeRange,
            UUID programId
    ) {
        Organization organization =
                organizationAuthorization.findAccessibleOrganization(
                        currentUserId(),
                        organizationId,
                        REQUIRED_PERMISSION
                );
        AnalyticsTimeRange range = AnalyticsTimeRange.fromWireValue(timeRange);
        requireOwnProgram(organization.getId(), programId);

        return analyticsCache.load(
                organization.getId(),
                organization.getName(),
                range,
                programId
        );
    }

    private void requireOwnProgram(UUID organizationId, UUID programId) {
        if (programId == null) {
            return;
        }
        programRepository.findById(programId)
                .filter(program -> program.getDeletedAt() == null)
                .filter(program -> organizationId.equals(
                        program.getOrganizationId()
                ))
                .map(Program::getId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program " + programId + " is not a program of this "
                                + "organization"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
