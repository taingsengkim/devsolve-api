package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberPermissionsRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse register(
            @Valid @RequestBody OrganizationRequest request
    ) {
        return organizationService.register(request);
    }


    @GetMapping("/me")
    public OrganizationResponse me() {
        return organizationService.me();
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

    @GetMapping("/me/members")
    public List<MemberResponse> getMyMembers() {
        return organizationService.getMyMembers();
    }

    @PostMapping("/me/members/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse inviteMember(
            @Valid @RequestBody InviteMemberRequest request
    ) {
        return organizationService.inviteMember(request);
    }

    @PatchMapping("/me/members/{userId}/role")
    public MemberResponse updateMemberRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return organizationService.updateMemberRole(userId, request);
    }

    @PatchMapping("/me/members/{userId}/permissions")
    public MemberResponse updateMemberPermissions(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberPermissionsRequest request
    ) {
        return organizationService.updateMemberPermissions(userId, request);
    }

    @DeleteMapping("/me/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID userId) {
        organizationService.removeMember(userId);
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
