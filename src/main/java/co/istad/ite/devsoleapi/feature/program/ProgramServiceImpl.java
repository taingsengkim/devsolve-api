package co.istad.ite.devsoleapi.feature.program;

import co.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import co.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import co.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import co.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import co.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import co.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramUpdateRepository programUpdateRepository;
    private final ProgramMapper mapper;

    @Override
    public Page<ProgramResponseDto> getPrograms(UUID organizationId, ProgramState state, String visibility,
                                                String engagementType, Boolean offersBounties, Pageable pageable) {
        Visibility visEnum = visibility != null ? Visibility.valueOf(visibility.toUpperCase()) : null;
        EngagementType engEnum = engagementType != null ? EngagementType.valueOf(engagementType.toUpperCase()) : null;

        var spec = ProgramSpecification.filterPrograms(organizationId, state, visEnum, engEnum, offersBounties);
        return programRepository.findAll(spec, pageable).map(mapper::toResponseDto);
    }

    @Override
    public ProgramResponseDto getProgramById(UUID id) {
        return programRepository.findById(id)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));
    }

    @Override
    public ProgramResponseDto getProgramByHandle(String handle) {
        return programRepository.findByHandle(handle)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));
    }

    @Override
    @Transactional
    public ProgramResponseDto createProgram(UUID organizationId, ProgramRequestDto dto) {
        Program program = mapper.toEntity(dto);
        program.setOrganizationId(organizationId);
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        // Handle the nested assets/rewards if present in DTO – omitted for brevity, but you'd set them here.
        Program saved = programRepository.save(program);
        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateProgram(UUID id, ProgramUpdateRequestDto dto) {
        Program existing = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));

        // Only allow updates if DRAFT or ACTIVE (business rule)
        if (existing.getState() == ProgramState.CLOSED) {
            throw new RuntimeException("Cannot update a closed program.");
        }

        mapper.updateEntity(dto, existing);
        Program updated = programRepository.save(existing);

        // Log update in program_updates
        ProgramUpdate updateLog = ProgramUpdate.builder()
                .program(existing)
                .changeSummary("Program details updated")
                .build();
        programUpdateRepository.save(updateLog);

        return mapper.toResponseDto(updated);
    }

    @Override
    @Transactional
    public ProgramResponseDto publishProgram(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));

        if (program.getState() != ProgramState.DRAFT) {
            throw new RuntimeException("Only DRAFT programs can be published.");
        }

        program.setState(ProgramState.ACTIVE);
        program.setSubmissionState(SubmissionState.APPROVED); // Admin approval mocked
        program.setStartedAcceptingAt(LocalDateTime.now());
        Program saved = programRepository.save(program);

        // Log
        ProgramUpdate updateLog = ProgramUpdate.builder()
                .program(program)
                .changeSummary("Program published and approved.")
                .build();
        programUpdateRepository.save(updateLog);

        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto pauseProgram(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));

        if (program.getState() != ProgramState.ACTIVE) {
            throw new RuntimeException("Only ACTIVE programs can be paused.");
        }

        program.setState(ProgramState.PAUSED);
        Program saved = programRepository.save(program);

        ProgramUpdate updateLog = ProgramUpdate.builder()
                .program(program)
                .changeSummary("Program paused.")
                .build();
        programUpdateRepository.save(updateLog);

        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto closeProgram(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program"));

        if (program.getState() == ProgramState.CLOSED) {
            throw new RuntimeException("Program is already closed.");
        }

        program.setState(ProgramState.CLOSED);
        Program saved = programRepository.save(program);

        ProgramUpdate updateLog = ProgramUpdate.builder()
                .program(program)
                .changeSummary("Program closed.")
                .build();
        programUpdateRepository.save(updateLog);

        return mapper.toResponseDto(saved);
    }

    @Override
    public Page<ProgramUpdateChangeLogDto> getProgramUpdates(UUID id, Pageable pageable) {
        // Ensure program exists
        if (!programRepository.existsById(id)) {
            throw new ResourceNotFoundException("Program");
        }
        return programUpdateRepository.findByProgramId(id, pageable).map(mapper::toUpdateDto);
    }
}