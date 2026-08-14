package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRejectionRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/programs")
@RequiredArgsConstructor
public class ProgramAdminController {

    private final ProgramService programService;

    @GetMapping
    public Page<ProgramManagementSummaryResponseDto> getProgramsForReview(
            @RequestParam(
                    defaultValue = "PENDING_REVIEW"
            )
            SubmissionState submissionState,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getProgramsForReview(
                submissionState,
                pageable
        );
    }

    @GetMapping("/{id}")
    public ProgramResponseDto getProgram(@PathVariable UUID id) {
        return programService.getProgramForAdmin(id);
    }

    @PatchMapping("/{id}/approve")
    public ProgramResponseDto approveProgram(@PathVariable UUID id) {
        return programService.approveProgram(id);
    }

    @PatchMapping("/{id}/reject")
    public ProgramResponseDto rejectProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramRejectionRequest request
    ) {
        return programService.rejectProgram(id, request.reason());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProgram(@PathVariable UUID id) {
        programService.removeProgramByAdmin(id);
    }
}
