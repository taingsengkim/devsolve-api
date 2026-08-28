package kh.edu.istad.ite.devsoleapi.feature.program;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingCache;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramHandleAvailabilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramViewCountResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

import static kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService.ORGANIZATION_PARAMETER_DESCRIPTION;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class ProgramController {

    private final ProgramService programService;

    @GetMapping("/programs")
    public ResponseEntity<Page<ProgramSummaryResponseDto>> getPublicPrograms(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) EngagementType engagementType,
            @RequestParam(required = false) Boolean offersBounties,
            @RequestParam(required = false)
            @Size(max = 100)
            String q,
            @RequestParam(required = false)
            @DecimalMin("0.0")
            BigDecimal minimumBounty,
            @RequestParam(required = false)
            @DecimalMin("0.0")
            BigDecimal maximumBounty,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) Severity maxSeverity,
            @RequestParam(required = false) Industry industry,
            @RequestParam(required = false)
            @Size(max = 100)
            String country,
            @PageableDefault(
                    size = 20,
                    sort = "publishedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return ListingCache.publicListing(programService.getPublicPrograms(
                organizationId,
                engagementType,
                offersBounties,
                q,
                minimumBounty,
                maximumBounty,
                assetType,
                maxSeverity,
                industry,
                country,
                pageable
        ));
    }

    @GetMapping("/programs/{id}")
    public PublicProgramResponseDto getPublicProgramById(
            @PathVariable UUID id
    ) {
        return programService.getPublicProgramById(id);
    }

    @GetMapping("/programs/handle/{handle}")
    public PublicProgramResponseDto getPublicProgramByHandle(
            @PathVariable String handle
    ) {
        return programService.getPublicProgramByHandle(handle);
    }

    @PostMapping("/programs/{id}/views")
    public ProgramViewCountResponseDto incrementViewCount(
            @PathVariable UUID id
    ) {
        return programService.incrementViewCount(id);
    }

    @GetMapping("/programs/{id}/updates")
    public Page<ProgramUpdateChangeLogDto> getPublicProgramUpdates(
            @PathVariable UUID id,
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getPublicProgramUpdates(id, pageable);
    }

    @GetMapping("/organizations/me/programs")
    public Page<ProgramManagementSummaryResponseDto> getMyPrograms(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getMyPrograms(organizationId, pageable);
    }

    /**
     * Mapped above {@code /organizations/me/programs/{id}} on purpose: the
     * literal segment wins over the variable, so "deleted" never reaches the
     * UUID converter.
     */
    @GetMapping("/organizations/me/programs/deleted")
    public Page<ProgramManagementSummaryResponseDto> getMyDeletedPrograms(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @PageableDefault(
                    size = 20,
                    sort = "deletedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getMyDeletedPrograms(organizationId, pageable);
    }

    /**
     * Whether a handle is still free, so the answer arrives beside the field
     * instead of on submit after four wizard steps.
     *
     * <p>Mapped above {@code /organizations/me/programs/{id}} for the same
     * reason {@code /deleted} is. Deliberately not
     * {@code GET /programs/handle/{handle}}, which resolves published programs
     * only: a draft holding the handle reads as 404 there, so a client would
     * report "available" for a name the write then rejects.
     */
    @GetMapping("/organizations/me/programs/handle-available")
    public ProgramHandleAvailabilityResponse checkHandleAvailability(
            @RequestParam String handle,
            @Parameter(description = "The program being edited, whose own "
                    + "handle should not count as taken. Omit when creating.")
            @RequestParam(required = false) UUID programId
    ) {
        return programService.checkHandleAvailability(handle, programId);
    }

    /**
     * Takes no organization: the program id already names one, and the caller
     * is checked for VIEW_PROGRAMS at that organization rather than at
     * whichever one they happen to be resolved to.
     */
    @GetMapping("/organizations/me/programs/{id}")
    public ProgramResponseDto getMyProgram(@PathVariable UUID id) {
        return programService.getMyProgram(id);
    }

    /**
     * Creates a program, as a draft unless {@code submit} says otherwise.
     *
     * <p>A draft may be incomplete: only {@code handle} and {@code name} are
     * required, and the rest is checked when the program is submitted. What is
     * supplied is still validated, so a malformed handle is refused either way.
     */
    @ApiResponse(
            responseCode = "400",
            description = "A supplied field is malformed, or — when submitting "
                    + "— a required one is missing. errorDetails maps each "
                    + "field to its message; violations names the constraint "
                    + "and its parameters.",
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = "The handle is already used by another program.",
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @PostMapping("/organizations/me/programs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramResponseDto createProgram(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Parameter(description = "Submit for admin review in the same "
                    + "transaction. Without it the program is created as a "
                    + "draft, and a failed follow-up submit leaves one behind.")
            @RequestParam(defaultValue = "false") boolean submit,
            @Valid @RequestBody ProgramRequestDto request
    ) {
        return programService.createProgram(organizationId, submit, request);
    }

    @PatchMapping({
            "/programs/{id}",
            "/organizations/me/programs/{id}"
    })
    public ProgramResponseDto updateProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramUpdateRequestDto request
    ) {
        return programService.updateProgram(id, request);
    }

    /**
     * Compatibility endpoint for editors that save with PUT. It deliberately
     * keeps PATCH-style omission/null semantics rather than replacing fields
     * the editor did not send. PATCH remains the canonical method; both apply
     * the same workflow rules, including leaving a draft as a draft.
     */
    @PutMapping({
            "/programs/{id}",
            "/organizations/me/programs/{id}"
    })
    public ProgramResponseDto saveProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramUpdateRequestDto request
    ) {
        return programService.updateProgram(id, request);
    }

    @PatchMapping("/programs/{id}/submit")
    public ProgramResponseDto submitProgram(@PathVariable UUID id) {
        return programService.submitProgram(id);
    }

    @PatchMapping({
            "/programs/{id}/publish",
            "/organizations/me/programs/{id}/publish"
    })
    public ProgramResponseDto publishProgram(@PathVariable UUID id) {
        return programService.publishProgram(id);
    }

    @PatchMapping("/programs/{id}/pause")
    public ProgramResponseDto pauseProgram(@PathVariable UUID id) {
        return programService.pauseProgram(id);
    }

    @PatchMapping("/programs/{id}/resume")
    public ProgramResponseDto resumeProgram(@PathVariable UUID id) {
        return programService.resumeProgram(id);
    }

    @PatchMapping("/programs/{id}/close")
    public ProgramResponseDto closeProgram(@PathVariable UUID id) {
        return programService.closeProgram(id);
    }

    @DeleteMapping("/programs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProgram(@PathVariable UUID id) {
        programService.deleteProgram(id);
    }

    @PostMapping("/programs/{id}/restore")
    public ProgramResponseDto restoreProgram(@PathVariable UUID id) {
        return programService.restoreProgram(id);
    }
}
