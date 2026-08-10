package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    @GetMapping("/programs")
    public Page<ProgramSummaryResponseDto> getPublicPrograms(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) EngagementType engagementType,
            @RequestParam(required = false) Boolean offersBounties,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getPublicPrograms(
                organizationId,
                engagementType,
                offersBounties,
                pageable
        );
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
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return programService.getMyPrograms(pageable);
    }

    @GetMapping("/organizations/me/programs/{id}")
    public ProgramResponseDto getMyProgram(@PathVariable UUID id) {
        return programService.getMyProgram(id);
    }

    @PostMapping("/organizations/me/programs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramResponseDto createProgram(
            @Valid @RequestBody ProgramRequestDto request
    ) {
        return programService.createProgram(request);
    }

    @PatchMapping("/programs/{id}")
    public ProgramResponseDto updateProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramUpdateRequestDto request
    ) {
        return programService.updateProgram(id, request);
    }

    @PatchMapping("/programs/{id}/submit")
    public ProgramResponseDto submitProgram(@PathVariable UUID id) {
        return programService.submitProgram(id);
    }

    @PatchMapping("/programs/{id}/publish")
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
}
