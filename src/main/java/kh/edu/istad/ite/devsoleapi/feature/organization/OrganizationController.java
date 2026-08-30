package kh.edu.istad.ite.devsoleapi.feature.organization;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationMembershipResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRoleResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.PendingInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberPermissionsRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService.ORGANIZATION_PARAMETER_DESCRIPTION;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private static final String NOT_A_MEMBER =
            "The caller is not at an active organization, or lacks the "
                    + "permission named in errorDetails.requiredPermission. "
                    + "Distinct from an empty result: nothing was withheld "
                    + "silently.";
    private static final String AMBIGUOUS_ORGANIZATION =
            "The caller is at more than one organization and named none. "
                    + "errorDetails.organizationIds lists them.";
    private static final String OUTSIDE_ROLE =
            "The permissions asked for are outside what the role allows. "
                    + "errorDetails names the role, the offending values and "
                    + "the allowed set.";

    private final OrganizationService organizationService;


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse register(
            @Valid @RequestBody OrganizationRequest request
    ) {
        return organizationService.register(request);
    }


    /**
     * The organization behind the caller's company workspace — owned or
     * joined.
     *
     * <p>{@code organizationId} answers for an account that belongs to more
     * than one. Omitting it asks for the only organization there is, and is
     * refused with 409 and the candidate ids when there is a choice to make.
     * Every {@code /me/*} route below reads it the same way.
     */
    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @GetMapping("/me")
    public OrganizationResponse me(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId
    ) {
        return organizationService.me(organizationId);
    }

    /**
     * What each role means: the permissions it starts with and the most it may
     * hold. Published so a client gating features on permissions reads this
     * table rather than keeping a copy that drifts from it.
     */
    @GetMapping("/roles")
    public List<OrganizationRoleResponse> getRoles() {
        return organizationService.getRoles();
    }

    @GetMapping("/me/verification")
    public OrganizationVerificationResponse getVerificationStatus() {
        return organizationService.getVerificationStatus();
    }

    @PostMapping("/me/verification-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerificationEmail() {
        organizationService.resendVerificationEmail();
    }

    @PatchMapping("/me")
    public OrganizationResponse updateMe(
            @Valid @RequestBody OrganizationUpdateRequest request
    ) {
        return organizationService.updateMe(request);
    }

    @PutMapping(
            value = "/me/logo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public OrganizationResponse uploadLogo(
            @RequestPart("file") MultipartFile file
    ) {
        return organizationService.uploadLogo(file);
    }

    @DeleteMapping("/me/logo")
    public OrganizationResponse removeLogo() {
        return organizationService.removeLogo();
    }

    @PutMapping(
            value = "/me/cover",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public OrganizationResponse uploadCoverImage(
            @RequestPart("file") MultipartFile file
    ) {
        return organizationService.uploadCoverImage(file);
    }

    @DeleteMapping("/me/cover")
    public OrganizationResponse removeCoverImage() {
        return organizationService.removeCoverImage();
    }

    @PostMapping("/me/resubmit")
    public OrganizationResponse resubmit() {
        return organizationService.resubmit();
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe() {
        organizationService.deleteMe();
    }

    @GetMapping("/{id}")
    public OrganizationResponse getById(@PathVariable UUID id) {
        return organizationService.getById(id);
    }

    @GetMapping("/slug/{slug}")
    public OrganizationResponse getBySlug(@PathVariable String slug) {
        return organizationService.getBySlug(slug);
    }

    /**
     * The whole team, owner included, for anybody on it.
     *
     * <p>Readable by any active member rather than the owner alone, so a
     * refusal here means the caller is at no such organization — not that the
     * team is empty. Managing the team still needs {@code MANAGE_MEMBERS}.
     */
    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @GetMapping("/me/members")
    public List<MemberResponse> getMyMembers(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId
    ) {
        return organizationService.getMyMembers(organizationId);
    }

    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "422",
            description = OUTSIDE_ROLE,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @PostMapping("/me/members/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse inviteMember(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return organizationService.inviteMember(organizationId, request);
    }

    /**
     * Also resets the member's permissions to the new role's defaults, since
     * a role that left the old set in place would make a demotion cosmetic.
     */
    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @PatchMapping("/me/members/{userId}/role")
    public MemberResponse updateMemberRole(
            @PathVariable UUID userId,
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return organizationService.updateMemberRole(
                organizationId,
                userId,
                request
        );
    }

    /**
     * Bounded by the member's role: a VIEWER may not be handed CREATE_PROGRAM.
     * {@code GET /api/v1/organizations/roles} publishes the table.
     */
    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "422",
            description = OUTSIDE_ROLE,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @PatchMapping("/me/members/{userId}/permissions")
    public MemberResponse updateMemberPermissions(
            @PathVariable UUID userId,
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Valid @RequestBody UpdateMemberPermissionsRequest request
    ) {
        return organizationService.updateMemberPermissions(
                organizationId,
                userId,
                request
        );
    }

    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @DeleteMapping("/me/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID userId,
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId
    ) {
        organizationService.removeMember(organizationId, userId);
    }

    /**
     * Which organizations the caller belongs to, and as what. Empty for an
     * account with no company at all, which is the answer a client needs on
     * sign-in before it can decide whether to offer a company workspace.
     *
     * <p>Sits beside /me rather than replacing it: /me answers with one
     * organization and needs to be told which when there is a choice, and this
     * is where the caller finds the ids to choose from.
     */
    @GetMapping("/me/memberships")
    public List<OrganizationMembershipResponse> getMyMemberships() {
        return organizationService.getMyMemberships();
    }

    /**
     * The caller's own open invitations. Sits under /invitations rather than
     * /me/members, which belongs to a company looking at its own team — this
     * is the other side of that conversation.
     */
    @GetMapping("/invitations/me")
    public List<PendingInvitationResponse> getMyInvitations() {
        return organizationService.getMyInvitations();
    }

    @PostMapping("/invitations/{token}/accept")
    public MemberResponse acceptInvitation(@PathVariable String token) {
        return organizationService.acceptInvitation(token);
    }


    @GetMapping("/{id}/programs")
    public List<ProgramSummaryResponseDto> getOrganizationPrograms(@PathVariable UUID id) {
         List<ProgramSummaryResponseDto> programs = organizationService.getOrganizationPrograms(id);
        return programs ;
    }

}
