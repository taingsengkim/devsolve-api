package co.istad.ite.devsoleapi.feature.program;

import co.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import co.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    // 1. GET /programs — Public — Browse public open programs (paginated, filterable)
    @GetMapping("/programs")
    public ResponseEntity<Page<ProgramResponseDto>> getPrograms(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) ProgramState state,
            @RequestParam(required = false) String visibility,
            @RequestParam(required = false) String engagementType,
            @RequestParam(required = false) Boolean offersBounties,
            @PageableDefault(size = 20 ) Pageable pageable
    ) {
        return ResponseEntity.ok(programService.getPrograms(
                organizationId, state, visibility, engagementType, offersBounties, pageable));
    }

    // 2. GET /programs/{id} — Public — Get program detail
    @GetMapping("/programs/{id}")
    public ResponseEntity<ProgramResponseDto> getProgramById(@PathVariable UUID id) {
        return ResponseEntity.ok(programService.getProgramById(id));
    }

    // 3. GET /programs/{handle} — Public — Get program by handle
    @GetMapping("/programs/handle/{handle}")
    public ResponseEntity<ProgramResponseDto> getProgramByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(programService.getProgramByHandle(handle));
    }

    // 4. POST /organizations/{id}/programs — Manager/Member — Create a program (draft)
    @PostMapping("/organizations/programs")
    public ResponseEntity<ProgramResponseDto> createProgram(
            @Valid @RequestBody ProgramRequestDto dto
    ) {
        return new ResponseEntity<>(programService.createProgram(dto), HttpStatus.CREATED);
    }

    // 5. PATCH /programs/{id} — Manager/Member — Update a program
    @PatchMapping("/programs/{id}")
    public ResponseEntity<ProgramResponseDto> updateProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramUpdateRequestDto dto
    ) {
        return ResponseEntity.ok(programService.updateProgram(id, dto));
    }

    // 6. PATCH /programs/{id}/publish — Manager — Submit for admin approval and publish
    @PatchMapping("/programs/{id}/publish")
    public ResponseEntity<ProgramResponseDto> publishProgram(@PathVariable UUID id) {
        return ResponseEntity.ok(programService.publishProgram(id));
    }

    // 7. PATCH /programs/{id}/pause — Manager — Pause a program
    @PatchMapping("/programs/{id}/pause")
    public ResponseEntity<ProgramResponseDto> pauseProgram(@PathVariable UUID id) {
        return ResponseEntity.ok(programService.pauseProgram(id));
    }

    // 8. PATCH /programs/{id}/close — Manager — Close a program
    @PatchMapping("/programs/{id}/close")
    public ResponseEntity<ProgramResponseDto> closeProgram(@PathVariable UUID id) {
        return ResponseEntity.ok(programService.closeProgram(id));
    }

    // 9. GET /programs/{id}/updates — Public — Program changelog
    @GetMapping("/programs/{id}/updates")
    public ResponseEntity<Page<ProgramUpdateChangeLogDto>> getProgramUpdates(
            @PathVariable UUID id,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(programService.getProgramUpdates(id, pageable));
    }

    // 10. GET /programs/{id}/reports — Member+ — List reports submitted to this program
    // Since you didn't provide the 'reports' table schema, I'll add a placeholder.
    // Once you define the Report entity, inject ReportService here.
    @GetMapping("/programs/{id}/reports")
    public ResponseEntity<String> getProgramReports(@PathVariable UUID id) {
        // TODO: Implement when Report entity is defined.
        return ResponseEntity.ok("Reports endpoint - implementation pending (Report entity not yet defined).");
    }
}