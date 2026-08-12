package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
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
import org.springframework.web.bind.annotation.RestController;


import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class HacktivityController {

    private final HacktivityService hacktivityService;
    private final OrganizationMemberRepository organizationMemberRepository;


    @GetMapping("/hacktivity")
    public Page<HacktivityResponse> getHacktivity(
            @PageableDefault(size = 10) Pageable pageable
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

        return hacktivityService.getMyHacktivity(
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


    @GetMapping("/organizations/me/hacktivity")
    @PreAuthorize("hasAnyRole('MEMBER', 'ADMIN')")
    public Page<HacktivityResponse> getMyOrganizationHacktivity(
            @AuthenticationPrincipal Jwt jwt,

            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        UUID userId = UUID.fromString(jwt.getSubject());

        OrganizationMember membership =
                organizationMemberRepository
                        .findByUserId(userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User is not a member of any organization: "
                                                + userId
                                )
                        );

        UUID organizationId = membership.getOrganization().getId();

        return hacktivityService.getMyOrganizationHacktivity(
                organizationId,
                pageable
        );
    }
}