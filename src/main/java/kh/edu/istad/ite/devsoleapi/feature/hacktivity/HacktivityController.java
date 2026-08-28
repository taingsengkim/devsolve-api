package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import io.swagger.v3.oas.annotations.Parameter;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
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

import java.util.UUID;

import static kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService.ORGANIZATION_PARAMETER_DESCRIPTION;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HacktivityController {

    private final HacktivityService hacktivityService;
    private final OrganizationAuthorizationService organizationAuthorization;


    @GetMapping("/hacktivity")
    public Page<HacktivityResponse> getHacktivity(
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return hacktivityService.findAll(pageable);
    }


    @GetMapping("/hacktivity/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<HacktivityResponse> getMyHacktivity(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        return hacktivityService.getUserHacktivity(
                userId,
                pageable
        );
    }


    @GetMapping("/user-profiles/{userId}/hacktivity")
    public Page<HacktivityResponse> getUserHacktivity(
            @PathVariable UUID userId,

            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return hacktivityService.getUserHacktivity(
                userId,
                pageable
        );
    }

    @GetMapping("/organizations/{orgId}/hacktivity")
    public Page<HacktivityResponse> getOrganizationHacktivity(
            @PathVariable UUID orgId,

            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return hacktivityService.getOrganizationHacktivity(
                orgId,
                pageable
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
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return hacktivityService.getOrganizationHacktivity(
                organizationAuthorization
                        .findAccessibleOrganization(userId, organizationId)
                        .getId(),
                pageable
        );
    }
}