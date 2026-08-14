package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.common.listing.ViewCountGuard;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowNotificationService;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.springframework.context.ApplicationEventPublisher;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.organization.CompanyIdentityService;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramManagementSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramUpdateRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramViewCountResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.dto.ProgramUpdateChangeLogDto;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final Set<String> PUBLIC_PROGRAM_SORT_PROPERTIES = Set.of(
            "id",
            "publishedAt",
            "createdAt",
            "updatedAt",
            "name",
            "handle",
            "minimumBounty",
            "maximumBounty",
            "viewCount",
            "followerCount",
            "totalSubmissions"
    );
    private static final Set<String> PROGRAM_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "name",
            "handle",
            "state",
            "submissionState"
    );
    private static final Set<String> DELETED_PROGRAM_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "name",
            "handle"
    );
    private static final Set<String> PROGRAM_UPDATE_SORT_PROPERTIES =
            Set.of("id", "createdAt");

    private final ProgramRepository programRepository;
    private final ProgramAssetRepository programAssetRepository;
    private final ProgramUpdateRepository programUpdateRepository;
    private final ProgramMapper mapper;
    private final OrganizationRepository organizationRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final FollowNotificationService followNotificationService;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;
    private final ViewCountGuard viewCountGuard;
    private final CompanyIdentityService companyIdentityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramSummaryResponseDto> getPublicPrograms(
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
    ) {
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PUBLIC_PROGRAM_SORT_PROPERTIES
        );
        validatePublicListingFilters(minimumBounty, maximumBounty);
        PublicProgramOrdering ordering = resolvePublicOrdering(
                validatedPageable
        );
        Pageable databasePageable = validatedPageable.isPaged()
                ? PageRequest.of(
                        validatedPageable.getPageNumber(),
                        validatedPageable.getPageSize()
                )
                : Pageable.unpaged();

        Page<Program> programs = programRepository.searchPublicPrograms(
                organizationId,
                databaseValue(engagementType),
                offersBounties,
                containsPattern(query, 100, "q"),
                minimumBounty,
                maximumBounty,
                databaseValue(assetType),
                databaseValue(maxSeverity),
                databaseValue(industry),
                normalizeExactFilter(country, 100, "country"),
                ordering.property(),
                ordering.direction(),
                databasePageable
        );
        PublicProgramContext context = loadPublicProgramContext(
                programs.getContent()
        );
        List<ProgramSummaryResponseDto> content = programs.stream()
                .map(program -> mapper.toSummaryDto(
                program,
                context.organizations().get(program.getOrganizationId()),
                context.assetsByProgram().getOrDefault(
                        program.getId(),
                        List.of()
                ),
                context.followerCounts().getOrDefault(program.getId(), 0L),
                context.submissionCounts().getOrDefault(program.getId(), 0L)
        )).toList();
        if (validatedPageable.isUnpaged()) {
            return new PageImpl<>(content);
        }
        return new PageImpl<>(
                content,
                validatedPageable,
                programs.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramById(UUID id) {
        return toPublicResponse(findPublicProgramById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramByHandle(String handle) {
        Program program = programRepository
                .findByHandle(normalizeHandle(handle))
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
        return toPublicResponse(program);
    }

    @Override
    @Transactional
    public ProgramViewCountResponseDto incrementViewCount(UUID id) {
        if (viewCountGuard.shouldCount("program", id)) {
            int updated = programRepository.incrementPublicViewCount(
                    id,
                    ProgramState.ACTIVE,
                    SubmissionState.APPROVED,
                    Visibility.PUBLIC
            );
            if (updated == 0) {
                throw programNotFound();
            }
        }

        Long viewCount = programRepository.findPublicViewCountById(
                id,
                ProgramState.ACTIVE,
                SubmissionState.APPROVED,
                Visibility.PUBLIC
        );
        if (viewCount == null) {
            throw programNotFound();
        }
        return new ProgramViewCountResponseDto(id, viewCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramManagementSummaryResponseDto> getMyPrograms(
            Pageable pageable
    ) {
        Organization organization = findAccessibleOrganization(
                OrganizationPermission.VIEW_PROGRAMS
        );
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.organizationPrograms(
                        organization.getId()
                ),
                validatedPageable
        );
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                loadProgramAssets(programs.getContent());
        return programs.map(program -> mapper.toManagementSummaryDto(
                program,
                organization,
                assetsByProgram.getOrDefault(program.getId(), List.of())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramManagementSummaryResponseDto> getMyDeletedPrograms(
            Pageable pageable
    ) {
        Organization organization = findAccessibleOrganization(
                OrganizationPermission.VIEW_PROGRAMS
        );
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                DELETED_PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.deletedOrganizationPrograms(
                        organization.getId()
                ),
                validatedPageable
        );
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                loadProgramAssets(programs.getContent());
        return programs.map(program -> mapper.toManagementSummaryDto(
                program,
                organization,
                assetsByProgram.getOrDefault(program.getId(), List.of())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getMyProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.VIEW_PROGRAMS
        );
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto createProgram(ProgramRequestDto request) {
        Organization organization = findAccessibleOrganization(
                OrganizationPermission.CREATE_PROGRAM
        );
        requireUniqueHandle(request.handle(), null);

        Program program = mapper.toEntity(request);
        program.setOrganizationId(organization.getId());
        program.setState(resolveInitialState(request.state()));
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        validateProgramConfiguration(program);

        Program saved = programRepository.saveAndFlush(program);
        ProgramUpdate update = logUpdate(saved,
                saved.getState() == ProgramState.ACTIVE
                        ? "Program created and submitted for admin review; "
                                + "launches on approval"
                        : "Program created and submitted for admin review");

        notifyAdministratorsOfReview(
                saved,
                update,
                "New program awaiting review"
        );

        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public ProgramResponseDto updateProgram(
            UUID id,
            ProgramUpdateRequestDto request
    ) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.EDIT_PROGRAM
        );
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Closed programs cannot be updated");
        }
        if (!hasChanges(request)) {
            throw badRequest("At least one program field must be updated");
        }

        if (request.handle() != null) {
            requireUniqueHandle(request.handle(), program.getId());
        }

        ProgramReviewConfiguration previousConfiguration =
                reviewConfiguration(program);
        Visibility previousVisibility = program.getVisibility();
        mapper.updateEntity(request, program);
        validateProgramConfiguration(program);
        boolean reviewSensitiveChanges = !previousConfiguration.equals(
                reviewConfiguration(program)
        );
        boolean visibilityChanged = previousVisibility
                != program.getVisibility();
        if (!reviewSensitiveChanges && !visibilityChanged) {
            return mapper.toResponseDto(program);
        }

        boolean requiresNewReview =
                program.getSubmissionState() == SubmissionState.APPROVED
                        && reviewSensitiveChanges;
        boolean becomingPublic = previousVisibility != Visibility.PUBLIC
                && program.getVisibility() == Visibility.PUBLIC;
        if (requiresNewReview) {
            program.setSubmissionState(SubmissionState.PENDING_REVIEW);
            program.setState(ProgramState.DRAFT);
            program.setVisibility(Visibility.PRIVATE);
            logUpdate(
                    program,
                    "Program details updated; admin approval requested again"
            );
        } else if (visibilityChanged && !reviewSensitiveChanges) {
            logUpdate(
                    program,
                    "Program visibility changed to "
                            + program.getVisibility()
                            .name()
                            .toLowerCase(Locale.ROOT)
            );
        } else {
            logUpdate(program, "Program details updated");
        }
        markPublishedIfPublic(program);
        if (becomingPublic && isPubliclyAccessible(program)) {
            notifyOrganizationFollowersOfPublishedProgram(program);
        }
        // Assign IDs to newly added assets/rewards before the response is
        // serialized and surface any persistence conflict on this request.
        programRepository.flush();
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto submitProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.EDIT_PROGRAM
        );
        if (program.getSubmissionState() != SubmissionState.REJECTED) {
            throw conflict(
                    "Only rejected programs can be resubmitted for review"
            );
        }

        validateProgramConfiguration(program);
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        ProgramUpdate update =
                logUpdate(program, "Program resubmitted for admin review");

        notifyAdministratorsOfReview(
                program,
                update,
                "Program resubmitted for review"
        );

        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto publishProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() != ProgramState.DRAFT) {
            throw conflict("Only draft programs can be launched");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved by an admin before launch"
            );
        }

        program.setState(ProgramState.ACTIVE);
        markPublishedIfPublic(program);
        logUpdate(program, "Program launched");
        if (program.getVisibility() == Visibility.PUBLIC) {
            notifyOrganizationFollowersOfPublishedProgram(program);
        }
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto pauseProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
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
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() != ProgramState.PAUSED) {
            throw conflict("Only paused programs can be resumed");
        }
        if (program.getSubmissionState() != SubmissionState.APPROVED) {
            throw conflict(
                    "Program must be approved before it can be resumed"
            );
        }

        program.setState(ProgramState.ACTIVE);
        markPublishedIfPublic(program);
        logUpdate(program, "Program resumed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public ProgramResponseDto closeProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.MANAGE_PROGRAM_STATE
        );
        if (program.getState() == ProgramState.CLOSED) {
            throw conflict("Program is already closed");
        }

        program.setState(ProgramState.CLOSED);
        logUpdate(program, "Program closed");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    public void deleteProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.DELETE_PROGRAM
        );
        program.setState(ProgramState.CLOSED);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(program, "Program deleted");
        program.setDeletedAt(LocalDateTime.now());
    }

    /**
     * Undoes a delete without republishing. Deleting closes the program and
     * makes it private, and restoring deliberately does not undo that: it
     * hands back a private draft the organization must relaunch on purpose.
     * Anything else would put a program back on the public internet — with
     * researchers notified — as the side effect of an undo click.
     *
     * <p>The admin decision rides along untouched, so a program that was
     * approved before the delete needs no second review. A deleted handle
     * stays reserved against {@code existsByHandleIgnoreCase}, so nothing
     * can have claimed it in the meantime.
     */
    @Override
    @Transactional
    public ProgramResponseDto restoreProgram(UUID id) {
        Program program = programRepository.findById(id)
                .orElseThrow(this::programNotFound);
        organizationAuthorization.requirePermission(
                program.getOrganizationId(),
                extractCurrentUserId(),
                OrganizationPermission.DELETE_PROGRAM
        );
        if (program.getDeletedAt() == null) {
            throw conflict("Program is not deleted");
        }

        program.setDeletedAt(null);
        program.setState(ProgramState.DRAFT);
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(program, "Program restored as a private draft");
        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramUpdateChangeLogDto> getPublicProgramUpdates(
            UUID id,
            Pageable pageable
    ) {
        findPublicProgramById(id);
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_UPDATE_SORT_PROPERTIES
        );
        return programUpdateRepository
                .findByProgramId(id, validatedPageable)
                .map(mapper::toUpdateDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramManagementSummaryResponseDto> getProgramsForReview(
            SubmissionState submissionState,
            Pageable pageable
    ) {
        requireRole(ADMIN_ROLE);
        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                PROGRAM_SORT_PROPERTIES
        );
        Page<Program> programs = programRepository.findAll(
                ProgramSpecification.programsForReview(submissionState),
                validatedPageable
        );
        Map<UUID, Organization> organizationsById = loadOrganizationsById(
                programs.getContent()
        );
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                loadProgramAssets(programs.getContent());
        return programs.map(program -> mapper.toManagementSummaryDto(
                program,
                organizationsById.get(program.getOrganizationId()),
                assetsByProgram.getOrDefault(program.getId(), List.of())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramResponseDto getProgramForAdmin(UUID id) {
        requireRole(ADMIN_ROLE);
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        return mapper.toResponseDto(program);
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
        markPublishedIfPublic(program);
        ProgramUpdate update =
                logUpdate(program, "Program approved by admin");

        notifyOrganizationOwner(
                organization,
                program,
                update,
                "Program approved",
                "\"" + program.getName()
                        + "\" has been approved and can now be published."
        );

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
        ProgramUpdate update = logUpdate(
                program,
                "Program rejected by admin: " + reason.trim()
        );

        organizationRepository.findById(program.getOrganizationId())
                .ifPresent(organization -> notifyOrganizationOwner(
                        organization,
                        program,
                        update,
                        "Program needs changes",
                        "\"" + program.getName()
                                + "\" was not approved: " + reason.trim()
                ));

        return mapper.toResponseDto(program);
    }

    /**
     * Keyed on the logged change rather than on the program, so a program
     * rejected, fixed, resubmitted and rejected again tells the owner every
     * round. Keying on the program alone would announce only the first.
     */
    private void notifyOrganizationOwner(
            Organization organization,
            Program program,
            ProgramUpdate update,
            String title,
            String content
    ) {
        eventPublisher.publishEvent(NotificationEvent.to(
                organization.getOwner() == null
                        ? null
                        : organization.getOwner().getId(),
                title,
                content,
                NotificationType.PROGRAM,
                program.getId(),
                "program-review:" + update.getId() + ":owner"
        ));
    }

    /**
     * Nothing reaches a researcher until an administrator has looked at the
     * program, so a submission nobody is told about simply sits in the queue.
     */
    private void notifyAdministratorsOfReview(
            Program program,
            ProgramUpdate update,
            String title
    ) {
        eventPublisher.publishEvent(new NotificationEvent(
                companyIdentityService.findUserIdsByRealmRole(ADMIN_ROLE),
                title,
                "\"" + program.getName() + "\" is waiting for review.",
                NotificationType.PROGRAM,
                program.getId(),
                "program-review:" + update.getId() + ":admins"
        ));
    }

    private Program findPublicProgramById(UUID id) {
        return programRepository.findById(id)
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
    }

    private Program findProgramForManagement(
            UUID id,
            OrganizationPermission permission
    ) {
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        organizationAuthorization.requirePermission(
                program.getOrganizationId(),
                extractCurrentUserId(),
                permission
        );
        return program;
    }

    private Program findPendingProgramForReview(UUID id) {
        Program program = programRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(this::programNotFound);
        if (program.getSubmissionState() != SubmissionState.PENDING_REVIEW) {
            throw conflict("Program is not pending admin review");
        }
        return program;
    }

    private Organization findAccessibleOrganization(
            OrganizationPermission permission
    ) {
        return organizationAuthorization.findSingleAccessibleOrganization(
                extractCurrentUserId(),
                permission
        );
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
        return program.getDeletedAt() == null
                && program.getState() == ProgramState.ACTIVE
                && program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC;
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
        validateGuidelines(
                "Proof of concept requirements",
                program.getProofOfConceptRequirements()
        );
        validateGuidelines(
                "Rules of engagement",
                program.getRulesOfEngagement()
        );
        validateGuidelines("Program exclusions", program.getExclusions());
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

    /**
     * The state a new program starts in, defaulting to {@code DRAFT}.
     *
     * <p>Creating as {@code ACTIVE} does not skip review: the submission state
     * is still {@code PENDING_REVIEW}, and the public listing requires both
     * {@code ACTIVE} and {@code APPROVED}. It only settles in advance what
     * happens once an admin approves — go live immediately, rather than wait
     * for a separate call to publish.
     *
     * <p>{@code PAUSED} and {@code CLOSED} describe a program that has already
     * run, so neither is a coherent starting point.
     */
    private ProgramState resolveInitialState(ProgramState requested) {
        if (requested == null) {
            return ProgramState.DRAFT;
        }
        if (requested != ProgramState.DRAFT
                && requested != ProgramState.ACTIVE) {
            throw badRequest(
                    "A program can only be created as DRAFT or ACTIVE"
            );
        }
        return requested;
    }

    private boolean hasChanges(ProgramUpdateRequestDto request) {
        return request.handle() != null
                || request.name() != null
                || request.description() != null
                || request.engagementType() != null
                || request.visibility() != null
                || request.policy() != null
                || request.proofOfConceptRequirements() != null
                || request.rulesOfEngagement() != null
                || request.exclusions() != null
                || request.offersBounties() != null
                || request.minimumBounty() != null
                || request.maximumBounty() != null
                || request.assets() != null
                || request.rewards() != null;
    }

    private ProgramReviewConfiguration reviewConfiguration(Program program) {
        Set<AssetConfiguration> assets = program.getAssets()
                .stream()
                .map(asset -> new AssetConfiguration(
                        asset.getAssetType(),
                        asset.getIdentifier(),
                        asset.getDescription(),
                        asset.getIsInScope(),
                        asset.getMaxSeverity()
                ))
                .collect(Collectors.toSet());
        Set<RewardConfiguration> rewards = program.getRewards()
                .stream()
                .map(reward -> new RewardConfiguration(
                        reward.getSeverity(),
                        normalizeAmount(reward.getMinAmount()),
                        normalizeAmount(reward.getMaxAmount()),
                        reward.getPoints()
                ))
                .collect(Collectors.toSet());

        return new ProgramReviewConfiguration(
                program.getHandle(),
                program.getName(),
                program.getDescription(),
                program.getEngagementType(),
                program.getPolicy(),
                program.getProofOfConceptRequirements(),
                program.getRulesOfEngagement(),
                program.getExclusions(),
                program.getOffersBounties(),
                normalizeAmount(program.getMinimumBounty()),
                normalizeAmount(program.getMaximumBounty()),
                assets,
                rewards
        );
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros();
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

    /**
     * @return the logged change, whose id identifies this particular edit —
     * the natural key for anything announcing it, since a program can be
     * rejected, fixed and rejected again and each round is its own news
     */
    private ProgramUpdate logUpdate(Program program, String summary) {
        UUID actorId = extractCurrentUserId();
        ProgramUpdate update = programUpdateRepository.save(
                ProgramUpdate.builder()
                .program(program)
                .changeSummary(summary)
                .changedBy(actorId)
                .build()
        );
        if (program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC) {
            followNotificationService.notifyFollowers(
                    FollowType.PROGRAM,
                    program.getId(),
                    actorId,
                    "Program updated",
                    summary,
                    NotificationType.PROGRAM,
                    program.getId(),
                    "program-update:" + update.getId()
            );
        }
        return update;
    }

    private void validateGuidelines(
            String fieldName,
            ProgramGuidelinesDto guidelines
    ) {
        if (guidelines == null) {
            throw badRequest(fieldName + " are required");
        }
        if (guidelines.description() == null
                || guidelines.description().isBlank()) {
            throw badRequest(fieldName + " description is required");
        }
        if (guidelines.description().length() > 2000) {
            throw badRequest(
                    fieldName + " description cannot exceed 2000 characters"
            );
        }
        if (guidelines.rules() == null || guidelines.rules().isEmpty()) {
            throw badRequest(fieldName + " must contain at least one rule");
        }
        if (guidelines.rules().size() > 50) {
            throw badRequest(fieldName + " cannot contain more than 50 rules");
        }
        boolean invalidRule = guidelines.rules().stream()
                .anyMatch(rule -> rule == null
                        || rule.isBlank()
                        || rule.length() > 500);
        if (invalidRule) {
            throw badRequest(
                    fieldName
                            + " rules must be non-blank and cannot exceed 500 characters"
            );
        }
    }

    private PublicProgramResponseDto toPublicResponse(Program program) {
        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(this::programNotFound);
        List<ProgramAsset> assets = programAssetRepository
                .findByProgramIdOrderByCreatedAtAsc(program.getId());
        ProgramRepository.PublicProgramStatistics statistics =
                programRepository.findPublicStatisticsByProgramId(
                        program.getId()
                );
        return mapper.toPublicResponseDto(
                program,
                organization,
                assets,
                statistics.getTotalResearchers(),
                statistics.getTotalSubmissions(),
                followRepository.countByFollowableTypeAndFollowableId(
                        FollowType.PROGRAM,
                        program.getId()
                )
        );
    }

    private PublicProgramContext loadPublicProgramContext(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return new PublicProgramContext(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }

        // The whole row, not just the name: the public listing now carries the
        // organization's profile, and this query already had to load it.
        Map<UUID, Organization> organizations = loadOrganizationsById(programs);

        Set<UUID> programIds = programs.stream()
                .map(Program::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<ProgramAsset>> assetsByProgram =
                programAssetRepository
                        .findInScopeByProgramIds(programIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                asset -> asset.getProgram().getId()
                        ));
        Map<UUID, Long> followerCounts = toCountMap(
                followRepository.countByFollowableIds(
                        FollowType.PROGRAM,
                        programIds
                )
        );
        Map<UUID, Long> submissionCounts = toCountMap(
                reportRepository.countByProgramIds(programIds)
        );

        return new PublicProgramContext(
                organizations,
                assetsByProgram,
                followerCounts,
                submissionCounts
        );
    }

    private Map<UUID, Organization> loadOrganizationsById(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        Set<UUID> organizationIds = programs.stream()
                .map(Program::getOrganizationId)
                .collect(Collectors.toSet());
        return organizationRepository.findAllById(organizationIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getId,
                        organization -> organization
                ));
    }

    private Map<UUID, List<ProgramAsset>> loadProgramAssets(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        Set<UUID> programIds = programs.stream()
                .map(Program::getId)
                .collect(Collectors.toSet());
        return programAssetRepository.findByProgramIds(programIds)
                .stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getProgram().getId()
                ));
    }

    private record PublicProgramContext(
            Map<UUID, Organization> organizations,
            Map<UUID, List<ProgramAsset>> assetsByProgram,
            Map<UUID, Long> followerCounts,
            Map<UUID, Long> submissionCounts
    ) {
    }

    private void notifyOrganizationFollowersOfPublishedProgram(
            Program program
    ) {
        followNotificationService.notifyFollowers(
                FollowType.ORGANIZATION,
                program.getOrganizationId(),
                extractCurrentUserId(),
                "New program published",
                program.getName(),
                NotificationType.PROGRAM,
                program.getId(),
                "program-published:" + program.getId()
        );
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

    private void validatePublicListingFilters(
            BigDecimal minimumBounty,
            BigDecimal maximumBounty
    ) {
        if (minimumBounty != null && minimumBounty.signum() < 0) {
            throw badRequest("minimumBounty must be greater than or equal to 0");
        }
        if (maximumBounty != null && maximumBounty.signum() < 0) {
            throw badRequest("maximumBounty must be greater than or equal to 0");
        }
        validateRange(minimumBounty, maximumBounty, "Bounty filter");
    }

    private PublicProgramOrdering resolvePublicOrdering(Pageable pageable) {
        List<Sort.Order> orders = pageable.getSort().toList();
        if (orders.size() > 1) {
            throw badRequest(
                    "Only one public program sort property may be requested"
            );
        }
        Sort.Order order = orders.isEmpty()
                ? Sort.Order.desc("publishedAt")
                : orders.getFirst();
        return new PublicProgramOrdering(
                order.getProperty(),
                order.getDirection().name()
        );
    }

    private String containsPattern(
            String value,
            int maximumLength,
            String fieldName
    ) {
        String normalized = normalizeFilter(value, maximumLength, fieldName);
        if (normalized == null) {
            return null;
        }
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private String normalizeExactFilter(
            String value,
            int maximumLength,
            String fieldName
    ) {
        return normalizeFilter(value, maximumLength, fieldName);
    }

    private String normalizeFilter(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > maximumLength) {
            throw badRequest(
                    fieldName + " cannot exceed " + maximumLength
                            + " characters"
            );
        }
        return normalized;
    }

    private String databaseValue(Enum<?> value) {
        return value == null
                ? null
                : value.name().toLowerCase(Locale.ROOT);
    }

    private Map<UUID, Long> toCountMap(
            Collection<IdCountProjection> counts
    ) {
        return counts.stream().collect(Collectors.toUnmodifiableMap(
                IdCountProjection::getId,
                IdCountProjection::getTotal
        ));
    }

    private void markPublishedIfPublic(Program program) {
        if (program.getPublishedAt() == null
                && isPubliclyAccessible(program)) {
            program.setPublishedAt(LocalDateTime.now());
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

    private record ProgramReviewConfiguration(
            String handle,
            String name,
            String description,
            EngagementType engagementType,
            String policy,
            ProgramGuidelinesDto proofOfConceptRequirements,
            ProgramGuidelinesDto rulesOfEngagement,
            ProgramGuidelinesDto exclusions,
            Boolean offersBounties,
            BigDecimal minimumBounty,
            BigDecimal maximumBounty,
            Set<AssetConfiguration> assets,
            Set<RewardConfiguration> rewards
    ) {
    }

    private record AssetConfiguration(
            AssetType assetType,
            String identifier,
            String description,
            Boolean inScope,
            Severity maxSeverity
    ) {
    }

    private record RewardConfiguration(
            Severity severity,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            Integer points
    ) {
    }

    private record PublicProgramOrdering(
            String property,
            String direction
    ) {
    }
}
