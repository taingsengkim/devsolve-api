package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.InviteToProgramRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramAccessRevocationResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramInvitationResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProgramInvitationController {

    private final ProgramInvitationService programInvitationService;

    /** Asks a researcher onto a program. Needs MANAGE_RESEARCHERS. */
    @PostMapping("/programs/{programId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramInvitationResponse invite(
            @PathVariable UUID programId,
            @Valid @RequestBody InviteToProgramRequest request
    ) {
        return programInvitationService.invite(programId, request);
    }

    /** The company's guest list for one program. */
    @GetMapping("/programs/{programId}/invitations")
    public Page<ProgramInvitationResponse> findForProgram(
            @PathVariable UUID programId,
            @RequestParam(required = false) ProgramInvitationStatus status,
            @PageableDefault(
                    size = 20,
                    sort = "invitedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programInvitationService.findForProgram(
                programId,
                status,
                pageable
        );
    }

    /** Withdraws a researcher's access, however they answered. */
    @PatchMapping("/programs/{programId}/invitations/{userId}/revoke")
    public ProgramInvitationResponse revoke(
            @PathVariable UUID programId,
            @PathVariable UUID userId
    ) {
        return programInvitationService.revoke(programId, userId);
    }

    /**
     * What one researcher holds across one company's programs.
     *
     * <p>The preview behind removing them. A company reaching this from a
     * security incident knows who uploaded the file and not which of their
     * programs that person is on, so this is what lets the confirmation say
     * "this removes them from 3 programs" instead of asking them to guess.
     */
    @GetMapping(
            "/organizations/{organizationId}/researchers/{userId}"
                    + "/program-invitations"
    )
    public List<ProgramInvitationResponse> findForOrganizationResearcher(
            @PathVariable UUID organizationId,
            @PathVariable UUID userId
    ) {
        return programInvitationService.findForOrganizationResearcher(
                organizationId,
                userId
        );
    }

    /**
     * A researcher's own invitations.
     *
     * <p>The only place a private program they have been asked onto can be
     * found: it is in no public listing, and that is the point of it.
     */
    @GetMapping("/me/program-invitations")
    public Page<ProgramInvitationResponse> findMine(
            @RequestParam(required = false) ProgramInvitationStatus status,
            @PageableDefault(
                    size = 20,
                    sort = "invitedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programInvitationService.findMine(status, pageable);
    }

    @PatchMapping("/me/program-invitations/{programId}/accept")
    public ProgramInvitationResponse accept(@PathVariable UUID programId) {
        return programInvitationService.accept(programId);
    }

    @PatchMapping("/me/program-invitations/{programId}/decline")
    public ProgramInvitationResponse decline(@PathVariable UUID programId) {
        return programInvitationService.decline(programId);
    }
}
