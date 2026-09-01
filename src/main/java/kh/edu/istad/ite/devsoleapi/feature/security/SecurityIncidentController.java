package kh.edu.istad.ite.devsoleapi.feature.security;

import io.swagger.v3.oas.annotations.Parameter;
import kh.edu.istad.ite.devsoleapi.feature.security.dto.SecurityIncidentResponse;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;
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

/**
 * The uploads the platform refused because a scanner called them dangerous.
 *
 * <p>Never public. An incident names a researcher, the company they were
 * testing, and a file hash — a feed of who tried to upload what at whom.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class SecurityIncidentController {

    private static final String SEARCH_DESCRIPTION =
            "Free text over uploader handle and email, filename, and SHA-256.";

    private static final String VERDICT_DESCRIPTION =
            "One of MALICIOUS or SUSPICIOUS. Only refused uploads are "
                    + "recorded, so CLEAN and PENDING never match.";

    private final SecurityIncidentService securityIncidentService;

    /** Every incident on the platform. */
    @GetMapping("/admin/security/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<SecurityIncidentResponse> getAllIncidents(

            @Parameter(description = SEARCH_DESCRIPTION)
            @RequestParam(required = false) String search,

            @Parameter(description = VERDICT_DESCRIPTION)
            @RequestParam(required = false)
            VirusTotalScanResponse.Verdict verdict,

            @RequestParam(required = false) UUID organizationId,

            @PageableDefault(
                    size = 20,
                    sort = "blockedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return securityIncidentService.search(
                organizationId,
                search,
                verdict,
                SecurityIncidentPaging.resolve(pageable)
        );
    }

    /**
     * One company's incidents.
     *
     * <p>Authorised against the same permission that already decides who may
     * read a report's contents. Somebody who cannot see the finding has no
     * business seeing what was uploaded to it.
     */
    @GetMapping("/organizations/{orgId}/security/incidents")
    @PreAuthorize("isAuthenticated()")
    public Page<SecurityIncidentResponse> getOrganizationIncidents(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal Jwt jwt,

            @Parameter(description = SEARCH_DESCRIPTION)
            @RequestParam(required = false) String search,

            @Parameter(description = VERDICT_DESCRIPTION)
            @RequestParam(required = false)
            VirusTotalScanResponse.Verdict verdict,

            @PageableDefault(
                    size = 20,
                    sort = "blockedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        // Authorisation is the service's, not this method's: deciding it
        // reads a member's lazily-loaded permissions, and a controller runs
        // with no Hibernate session open to load them through.
        return securityIncidentService.searchForOrganization(
                orgId,
                UUID.fromString(jwt.getSubject()),
                search,
                verdict,
                SecurityIncidentPaging.resolve(pageable)
        );
    }
}
