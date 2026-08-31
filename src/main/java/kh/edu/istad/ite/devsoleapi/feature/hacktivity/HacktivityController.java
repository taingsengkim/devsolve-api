package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import io.swagger.v3.oas.annotations.Parameter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityFilter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService.ORGANIZATION_PARAMETER_DESCRIPTION;

/**
 * The public activity stream, and the same stream scoped to one researcher or
 * one company.
 *
 * <p>All of them take the same filters, so a company page and a program page
 * reuse this feed rather than each growing a variant of it. Filtering happens
 * in the database: a client can only filter what it has downloaded, which
 * makes a search box that searches one page.
 *
 * <p>Page size is capped at {@value HacktivityPaging#MAX_PAGE_SIZE} and
 * {@code sort} is an allow-list — see {@link HacktivityPaging}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HacktivityController {

    private static final String SEVERITY_DESCRIPTION =
            "Repeatable. One of NONE, LOW, MEDIUM, HIGH, CRITICAL.";

    private static final String EVENT_TYPE_DESCRIPTION =
            "Repeatable. One of RECOGNITION_AWARDED, BOUNTY_AWARDED, "
                    + "REPORT_DISCLOSED, REPORT_RESOLVED.";

    private static final String SORT_DESCRIPTION =
            "createdAt or severity, each with an optional ,ASC or ,DESC. "
                    + "Defaults to createdAt,DESC.";

    private final HacktivityService hacktivityService;
    private final OrganizationAuthorizationService organizationAuthorization;


    @GetMapping("/hacktivity")
    public Page<HacktivityResponse> getHacktivity(

            @Parameter(description = "Free text over researcher handle and "
                    + "name, program name and report title")
            @RequestParam(required = false) String q,

            @Parameter(description = SEVERITY_DESCRIPTION)
            @RequestParam(required = false) List<Severity> severity,

            @Parameter(description = EVENT_TYPE_DESCRIPTION)
            @RequestParam(required = false) List<HacktivityEventType> eventType,

            @RequestParam(required = false) UUID programId,

            @RequestParam(required = false) UUID organizationId,

            @Parameter(description = SORT_DESCRIPTION)
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return hacktivityService.search(
                new HacktivityFilter(
                        null,
                        organizationId,
                        programId,
                        q,
                        severity,
                        eventType
                ),
                HacktivityPaging.resolve(pageable)
        );
    }


    /**
     * The four numbers above the feed. Counted over the whole stream, so they
     * do not change as the reader pages.
     */
    @GetMapping("/hacktivity/stats")
    public HacktivityStatsResponse getHacktivityStats() {
        return hacktivityService.getStats();
    }


    @GetMapping("/hacktivity/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<HacktivityResponse> getMyHacktivity(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam(required = false) String q,

            @Parameter(description = SEVERITY_DESCRIPTION)
            @RequestParam(required = false) List<Severity> severity,

            @Parameter(description = EVENT_TYPE_DESCRIPTION)
            @RequestParam(required = false) List<HacktivityEventType> eventType,

            @RequestParam(required = false) UUID programId,

            @Parameter(description = SORT_DESCRIPTION)
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        return hacktivityService.search(
                new HacktivityFilter(
                        userId, null, programId, q, severity, eventType
                ),
                HacktivityPaging.resolve(pageable)
        );
    }


    @GetMapping("/user-profiles/{userId}/hacktivity")
    public Page<HacktivityResponse> getUserHacktivity(
            @PathVariable UUID userId,

            @RequestParam(required = false) String q,

            @Parameter(description = SEVERITY_DESCRIPTION)
            @RequestParam(required = false) List<Severity> severity,

            @Parameter(description = EVENT_TYPE_DESCRIPTION)
            @RequestParam(required = false) List<HacktivityEventType> eventType,

            @RequestParam(required = false) UUID programId,

            @Parameter(description = SORT_DESCRIPTION)
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return hacktivityService.search(
                new HacktivityFilter(
                        userId, null, programId, q, severity, eventType
                ),
                HacktivityPaging.resolve(pageable)
        );
    }


    @GetMapping("/organizations/{orgId}/hacktivity")
    public Page<HacktivityResponse> getOrganizationHacktivity(
            @PathVariable UUID orgId,

            @RequestParam(required = false) String q,

            @Parameter(description = SEVERITY_DESCRIPTION)
            @RequestParam(required = false) List<Severity> severity,

            @Parameter(description = EVENT_TYPE_DESCRIPTION)
            @RequestParam(required = false) List<HacktivityEventType> eventType,

            @RequestParam(required = false) UUID programId,

            @Parameter(description = SORT_DESCRIPTION)
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return hacktivityService.search(
                new HacktivityFilter(
                        null, orgId, programId, q, severity, eventType
                ),
                HacktivityPaging.resolve(pageable)
        );
    }


    /**
     * Resolved through the shared organization lookup rather than a membership
     * row of its own, so an owner — who has no such row — reaches their own
     * company's hacktivity, and an account at two companies is asked which one
     * instead of failing on a lookup that expects exactly one.
     */
    @GetMapping("/organizations/me/hacktivity")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public Page<HacktivityResponse> getMyOrganizationHacktivity(
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,

            @RequestParam(required = false) String q,

            @Parameter(description = SEVERITY_DESCRIPTION)
            @RequestParam(required = false) List<Severity> severity,

            @Parameter(description = EVENT_TYPE_DESCRIPTION)
            @RequestParam(required = false) List<HacktivityEventType> eventType,

            @RequestParam(required = false) UUID programId,

            @Parameter(description = SORT_DESCRIPTION)
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        UUID resolved = organizationAuthorization
                .findAccessibleOrganization(userId, organizationId)
                .getId();

        return hacktivityService.search(
                new HacktivityFilter(
                        null, resolved, programId, q, severity, eventType
                ),
                HacktivityPaging.resolve(pageable)
        );
    }
}
