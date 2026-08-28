package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.InviteResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReportingEligibilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RequestResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherAccessResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReviewResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ResearcherAccessController {

    private final ResearcherAccessService researcherAccessService;

    @PostMapping("/organizations/{organizationId}/researchers")
    @ResponseStatus(HttpStatus.CREATED)
    public ResearcherAccessResponse request(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RequestResearcherAccessRequest request
    ) {
        return researcherAccessService.request(organizationId, request);
    }

    @GetMapping("/organizations/{organizationId}/researchers")
    public Page<ResearcherAccessResponse> findForOrganization(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) ResearcherAccessStatus status,
            @PageableDefault(
                    size = 20,
                    sort = "requestedAt",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return researcherAccessService.findForOrganization(
                organizationId,
                status,
                pageable
        );
    }

    @PostMapping("/organizations/{organizationId}/researchers/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public ResearcherAccessResponse invite(
            @PathVariable UUID organizationId,
            @Valid @RequestBody InviteResearcherRequest request
    ) {
        return researcherAccessService.invite(organizationId, request);
    }

    @PatchMapping("/organizations/{organizationId}/researchers/{userId}")
    public ResearcherAccessResponse review(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId,
            @Valid @RequestBody ReviewResearcherAccessRequest request
    ) {
        return researcherAccessService.review(
                organizationId,
                userId,
                request
        );
    }

    @GetMapping("/organizations/{organizationId}/researchers/me")
    public ResearcherAccessResponse findMineForOrganization(
            @PathVariable UUID organizationId
    ) {
        return researcherAccessService.findMineForOrganization(
                organizationId
        );
    }

    @GetMapping("/researchers/me/access")
    public Page<ResearcherAccessResponse> findMine(
            @RequestParam(required = false) ResearcherAccessStatus status,
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return researcherAccessService.findMine(status, pageable);
    }

    @GetMapping("/programs/{programId}/reporting-access")
    public ReportingEligibilityResponse checkProgramEligibility(
            @PathVariable UUID programId
    ) {
        return researcherAccessService.checkProgramEligibility(programId);
    }
}
