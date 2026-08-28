package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramViewCountResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProgramService {

    Page<ProgramSummaryResponseDto> getPublicPrograms(
            UUID organizationId,
            EngagementType engagementType,
            Boolean offersBounties,
            String query,
            BigDecimal minimumBounty,
            BigDecimal maximumBounty,
            AssetType assetType,
            Severity maxSeverity,
            Industry industry,
            String country,
            Pageable pageable
    );

    PublicProgramResponseDto getPublicProgramById(UUID id);

    PublicProgramResponseDto getPublicProgramByHandle(String handle);

    ProgramViewCountResponseDto incrementViewCount(UUID id);

    Page<ProgramManagementSummaryResponseDto> getMyPrograms(
            UUID organizationId,
            Pageable pageable
    );

    Page<ProgramManagementSummaryResponseDto> getMyDeletedPrograms(
            UUID organizationId,
            Pageable pageable
    );

    ProgramResponseDto getMyProgram(UUID id);

    ProgramResponseDto createProgram(
            UUID organizationId,
            ProgramRequestDto request
    );

    ProgramResponseDto updateProgram(
            UUID id,
            ProgramUpdateRequestDto request
    );

    ProgramResponseDto submitProgram(UUID id);

    ProgramResponseDto publishProgram(UUID id);

    ProgramResponseDto pauseProgram(UUID id);

    ProgramResponseDto resumeProgram(UUID id);

    ProgramResponseDto closeProgram(UUID id);

    void deleteProgram(UUID id);

    void removeProgramByAdmin(UUID id);

    ProgramResponseDto restoreProgram(UUID id);

    Page<ProgramUpdateChangeLogDto> getPublicProgramUpdates(
            UUID id,
            Pageable pageable
    );

    Page<ProgramManagementSummaryResponseDto> getProgramsForReview(
            SubmissionState submissionState,
            Pageable pageable
    );

    ProgramResponseDto getProgramForAdmin(UUID id);

    ProgramResponseDto approveProgram(UUID id);

    ProgramResponseDto rejectProgram(UUID id, String reason);
}
