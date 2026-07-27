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
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String COMPANY_ROLE = "COMPANY";

    private final ProgramRepository programRepository;
    private final ProgramUpdateRepository programUpdateRepository;
    private final ProgramMapper mapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramResponseDto> getPublicPrograms(
            UUID organizationId,
            EngagementType engagementType,
            Boolean offersBounties,
            Pageable pageable
    ) {
        return programRepository.findAll(
                        ProgramSpecification.publicPrograms(
                                organizationId,
                                engagementType,
                                offersBounties
                        ),
                        pageable
                )
                .map(mapper::toResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getPublicProgramById(UUID id) {
        return mapper.toResponseDto(findPublicProgramById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getPublicProgramByHandle(String handle) {
        Program program = programRepository
                .findByHandle(normalizeHandle(handle))
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramResponseDto> getMyPrograms(Pageable pageable) {
        Organization organization = findManageableOrganization();
        return programRepository.findAll(
                        ProgramSpecification.organizationPrograms(
                                organization.getId()
                        ),
                        pageable
                )
                .map(mapper::toResponseDto);
    }

    @Override
    @Transactional
    public ProgramResponseDto createProgram(ProgramRequestDto request) {
        Organization organization = findManageableOrganization();
        requireUniqueHandle(request.handle(), null);
        requireAllowedVisibility(
                request.visibility(),
                SubmissionState.PENDING_REVIEW
        );

        Program program = mapper.toEntity(request);
        program.setOrganizationId(organization.getId());
        program.setState(ProgramState.DRAFT);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        validateProgramConfiguration(program);

        Program saved = programRepository.saveAndFlush(program);
        logUpdate(saved, "Program created and submitted for admin review");
        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateProgram(
            UUID id,
            ProgramUpdateRequestDto request
    ) {
        Program program = findProgramForManagement(id);
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Closed programs cannot be updated");
        }
        if (!hasChanges(request)) {
            throw badRequest("At least one program field must be updated");
        }

        if (request.handle() != null) {
            requireUniqueHandle(request.handle(), program.getId());
        }
        if (request.visibility() != null) {
            requireAllowedVisibility(
                    request.visibility(),
                    program.getSubmissionState()
            );
        }

        boolean requiresNewReview =
                program.getSubmissionState() == SubmissionState.APPROVED
                        && hasReviewSensitiveChanges(request);
        mapper.updateEntity(request, program);
        validateProgramConfiguration(program);
        if (requiresNewReview) {
            program.setSubmissionState(SubmissionState.PENDING_REVIEW);
            program.setState(ProgramState.DRAFT);
            program.setVisibility(Visibility.PRIVATE);
            logUpdate(
                    program,
                    "Program details updated; admin approval requested again"
            );
        } else if (request.visibility() != null
                && !hasReviewSensitiveChanges(request)) {
            logUpdate(
                    program,
                    "Program visibility changed to "
                            + request.visibility()
                            .name()
                            .toLowerCase(Locale.ROOT)
            );
        } else {
            logUpdate(program, "Program details updated");
        }
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto submitProgram(UUID id) {
        Program program = findProgramForManagement(id);
        if (program.getSubmissionState() != SubmissionState.REJECTED) {
            throw conflict(
                    "Only rejected programs can be resubmitted for review"
            );
        }

        validateProgramConfiguration(program);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(program, "Program resubmitted for admin review");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto publishProgram(UUID id) {
        Program program = findProgramForManagement(id);
        if (program.getState() != ProgramState.DRAFT) {
            throw conflict("Only draft programs can be launched");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved by an admin before launch"
            );
        }

        program.setState(ProgramState.ACTIVE);
        logUpdate(program, "Program launched");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto pauseProgram(UUID id) {
        Program program = findProgramForManagement(id);
        if (program.getState() != ProgramState.ACTIVE) {
            throw conflict("Only active programs can be paused");
        }

        program.setState(ProgramState.PAUSED);
        logUpdate(program, "Program paused");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto resumeProgram(UUID id) {
        Program program = findProgramForManagement(id);
        if (program.getState() != ProgramState.PAUSED) {
            throw conflict("Only paused programs can be resumed");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved before it can be resumed"
            );
        }

        program.setState(ProgramState.ACTIVE);
        logUpdate(program, "Program resumed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto closeProgram(UUID id) {
        Program program = findProgramForManagement(id);
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Program is already closed");
        }

        program.setState(ProgramState.CLOSED);
        logUpdate(program, "Program closed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramUpdateChangeLogDto> getPublicProgramUpdates(
            UUID id,
            Pageable pageable
    ) {
        findPublicProgramById(id);
        return programUpdateRepository
                .findByProgramId(id, pageable)
                .map(mapper::toUpdateDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramResponseDto> getProgramsForReview(
            SubmissionState submissionState,
            Pageable pageable
    ) {
        requireRole(ADMIN_ROLE);
        return programRepository.findAll(
                        ProgramSpecification.programsForReview(
                                submissionState
                        ),
                        pageable
                )
                .map(mapper::toResponseDto);
    }

    @Override
    @Transactional
    public ProgramResponseDto approveProgram(UUID id) {
        requireRole(ADMIN_ROLE);
        Program program = findPendingProgramForReview(id);
        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found for this program"
                ));
        requireApprovedOrganization(organization);
        validateProgramConfiguration(program);

        program.setSubmissionState(SubmissionState.APPROVED);
        logUpdate(program, "Program approved by admin");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto rejectProgram(UUID id, String reason) {
        requireRole(ADMIN_ROLE);
        Program program = findPendingProgramForReview(id);

        program.setSubmissionState(SubmissionState.REJECTED);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(
                program,
                "Program rejected by admin: " + reason.trim()
        );
        return mapper.toResponseDto(program);
    }

    private Program findPublicProgramById(UUID id) {
        return programRepository.findById(id)
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
    }

    private Program findProgramForManagement(UUID id) {
        requireRole(COMPANY_ROLE);
        Program program = programRepository.findById(id)
                .orElseThrow(this::programNotFound);
        requireManagementAccess(program.getOrganizationId());
        return program;
    }

    private Program findPendingProgramForReview(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(this::programNotFound);
        if (program.getSubmissionState() != SubmissionState.PENDING_REVIEW) {
            throw conflict("Program is not pending admin review");
        }
        return program;
    }

    private Organization findManageableOrganization() {
        requireRole(COMPANY_ROLE);
        UUID userId = extractCurrentUserId();

        return organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId)
                .map(this::requireApprovedOrganization)
                .orElseGet(() -> findManagedOrganization(userId));
    }

    private Organization findManagedOrganization(UUID userId) {
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
            throw conflict(
                    "The authenticated user manages multiple organizations"
            );
        }
        return requireApprovedOrganization(
                managedMemberships.getFirst().getOrganization()
        );
    }

    private void requireManagementAccess(UUID organizationId) {
        UUID userId = extractCurrentUserId();
        Organization organization = organizationRepository
                .findById(organizationId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Organization not found for this program"
                ));
        requireApprovedOrganization(organization);

        if (organization.getOwner().getId().equals(userId)) {
            return;
        }

        boolean isActiveManager = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .filter(member ->
                        member.getStatus() == MembershipStatus.ACTIVE
                )
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

    private Organization requireApprovedOrganization(
            Organization organization
    ) {
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw conflict(
                    "Organization must be approved before managing programs"
            );
        }
        return organization;
    }

    private boolean isPubliclyAccessible(Program program) {
        return program.getState() == ProgramState.ACTIVE
                && program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC;
    }

    private void requireAllowedVisibility(
            Visibility visibility,
            SubmissionState submissionState
    ) {
        if (visibility == Visibility.PUBLIC
                && submissionState != SubmissionState.APPROVED) {
            throw conflict("Only admin-approved programs can be public");
        }
    }

    private void requireUniqueHandle(String handle, UUID excludedId) {
        String normalizedHandle = normalizeHandle(handle);
        boolean exists = excludedId == null
                ? programRepository.existsByHandleIgnoreCase(normalizedHandle)
                : programRepository.existsByHandleIgnoreCaseAndIdNot(
                        normalizedHandle,
                        excludedId
                );
        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Program handle is already in use"
            );
        }
    }

    private void validateProgramConfiguration(Program program) {
        if (program.getPolicy() == null || program.getPolicy().isBlank()) {
            throw badRequest("Program policy is required");
        }
        if (program.getAssets().isEmpty()) {
            throw badRequest("At least one program asset is required");
        }

        validateRange(
                program.getMinimumBounty(),
                program.getMaximumBounty(),
                "Program bounty"
        );
        validateAssets(program.getAssets());
        validateRewards(program);
    }

    private boolean hasChanges(ProgramUpdateRequestDto request) {
        return request.handle() != null
                || request.name() != null
                || request.description() != null
                || request.engagementType() != null
                || request.visibility() != null
                || request.policy() != null
                || request.offersBounties() != null
                || request.minimumBounty() != null
                || request.maximumBounty() != null
                || request.assets() != null
                || request.rewards() != null;
    }

    private boolean hasReviewSensitiveChanges(
            ProgramUpdateRequestDto request
    ) {
        return request.handle() != null
                || request.name() != null
                || request.description() != null
                || request.engagementType() != null
                || request.policy() != null
                || request.offersBounties() != null
                || request.minimumBounty() != null
                || request.maximumBounty() != null
                || request.assets() != null
                || request.rewards() != null;
    }

    private void validateAssets(List<ProgramAsset> assets) {
        Set<String> uniqueAssets = new HashSet<>();
        for (ProgramAsset asset : assets) {
            String key = asset.getAssetType()
                    + ":"
                    + asset.getIdentifier().trim().toLowerCase(Locale.ROOT);
            if (!uniqueAssets.add(key)) {
                throw badRequest(
                        "Program assets must be unique by type and identifier"
                );
            }
        }
    }

    private void validateRewards(Program program) {
        if (Boolean.TRUE.equals(program.getOffersBounties())
                && program.getRewards().isEmpty()) {
            throw badRequest(
                    "At least one reward tier is required when bounties are enabled"
            );
        }

        Set<Object> severities = new HashSet<>();
        for (ProgramReward reward : program.getRewards()) {
            if (!severities.add(reward.getSeverity())) {
                throw badRequest(
                        "Only one reward tier is allowed per severity"
                );
            }
            validateRange(
                    reward.getMinAmount(),
                    reward.getMaxAmount(),
                    "Reward amount"
            );
        }
    }

    private void validateRange(
            BigDecimal minimum,
            BigDecimal maximum,
            String label
    ) {
        if (minimum != null
                && maximum != null
                && maximum.compareTo(minimum) < 0) {
            throw badRequest(
                    label + " maximum must be greater than or equal to minimum"
            );
        }
    }

    private void logUpdate(Program program, String summary) {
        programUpdateRepository.save(ProgramUpdate.builder()
                .program(program)
                .changeSummary(summary)
                .changedBy(extractCurrentUserId())
                .build());
    }

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + role
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

    private String normalizeHandle(String handle) {
        return handle.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ResourceNotFoundException programNotFound() {
        return new ResourceNotFoundException("Program not found");
    }
}
