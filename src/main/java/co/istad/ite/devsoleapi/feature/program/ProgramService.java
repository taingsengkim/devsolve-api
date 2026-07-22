package co.istad.ite.devsoleapi.feature.program;

import co.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import co.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProgramService {
    Page<ProgramResponseDto> getPrograms(UUID organizationId, ProgramState state, String visibility,
                                         String engagementType, Boolean offersBounties, Pageable pageable);
    ProgramResponseDto getProgramById(UUID id);
    ProgramResponseDto getProgramByHandle(String handle);
    ProgramResponseDto createProgram(UUID organizationId, ProgramRequestDto dto);
    ProgramResponseDto updateProgram(UUID id, ProgramUpdateRequestDto dto);
    ProgramResponseDto publishProgram(UUID id);
    ProgramResponseDto pauseProgram(UUID id);
    ProgramResponseDto closeProgram(UUID id);
    Page<ProgramUpdateChangeLogDto> getProgramUpdates(UUID id, Pageable pageable);
    // For /reports, we need a Report entity – I'll leave a placeholder comment.
}