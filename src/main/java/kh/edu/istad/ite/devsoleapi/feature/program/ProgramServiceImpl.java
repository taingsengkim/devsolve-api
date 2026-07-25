package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramRepository programRepository;
    private final ProgramUpdateRepository programUpdateRepository;
    private final ProgramMapper mapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

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
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with this ID"));
    }

    @Override
    public ProgramResponseDto getProgramByHandle(String handle) {
        return programRepository.findByHandle(handle)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Program not found with this Handle"));
    }

    @Override
    @Transactional
    public ProgramResponseDto createProgram(ProgramRequestDto dto) {
        Organization organization = findManageableOrganization();

        Program program = mapper.toEntity(dto);
        program.setOrganizationId(organization.getId());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        // Handle the nested assets/rewards if present in DTO – omitted for brevity, but you'd set them here.
        Program saved = programRepository.save(program);
        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateProgram(UUID id, ProgramUpdateRequestDto dto) {
        Program existing = findProgramForManagement(id);

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
        Program program = findProgramForManagement(id);

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
        Program program = findProgramForManagement(id);

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
        Program program = findProgramForManagement(id);

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
        findProgramForManagement(id);
        return programUpdateRepository.findByProgramId(id, pageable).map(mapper::toUpdateDto);
    }

    private Program findProgramForManagement(UUID programId) {
        requireCompanyRole();
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program not found with this id"
                ));
        requireManagementAccess(program.getOrganizationId());
        return program;
    }

    private Organization findManageableOrganization() {
        requireCompanyRole();
        UUID userId = extractCurrentUserId();

        return organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId)
                .map(this::requireApproved)
                .orElseGet(() -> {
                    List<OrganizationMember> managedMemberships =
                            organizationMemberRepository
                                    .findByUserIdAndStatus(
                                            userId,
                                            MembershipStatus.ACTIVE
                                    )
                                    .stream()
                                    .filter(member -> member.getRole() == OrgRole.MANAGER)
                                    .toList();

                    if (managedMemberships.isEmpty()) {
                        throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "The authenticated company cannot manage an organization"
                        );
                    }
                    if (managedMemberships.size() > 1) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "The authenticated user manages multiple organizations"
                        );
                    }
                    return requireApproved(
                            managedMemberships.getFirst().getOrganization()
                    );
                });
    }

    private void requireManagementAccess(UUID organizationId) {
        UUID userId = extractCurrentUserId();
        Organization organization = organizationRepository.findById(organizationId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found for this program"
                ));
        requireApproved(organization);

        if (organization.getOwner().getId().equals(userId)) {
            return;
        }

        boolean isActiveManager = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .map(OrganizationMember::getRole)
                .filter(role -> role == OrgRole.MANAGER)
                .isPresent();
        if (!isActiveManager) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the organization owner or a manager can manage this program"
            );
        }
    }

    private Organization requireApproved(Organization organization) {
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organization must be approved before managing programs"
            );
        }
        return organization;
    }

    private void requireCompanyRole() {
        if (!AuthUtils.hasRole("COMPANY")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only COMPANY accounts can manage programs"
            );
        }
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
