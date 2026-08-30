package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
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
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramHandleAvailabilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramListingSlice;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
    private final ProgramListingCache programListingCache;
    private final ProgramDetailCache programDetailCache;

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
        // Cached only when nothing is filtered — see ProgramListingCache. An
        // unpaged request passes a size of zero, which that cache declines.
        ProgramListingSlice slice = programListingCache.load(
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
                validatedPageable.isPaged()
                        ? validatedPageable.getPageNumber()
                        : 0,
                validatedPageable.isPaged()
                        ? validatedPageable.getPageSize()
                        : 0
        );

        if (validatedPageable.isUnpaged()) {
            return new PageImpl<>(slice.content());
        }
        return new PageImpl<>(
                slice.content(),
                validatedPageable,
                slice.totalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramById(UUID id) {
        // Resolved fresh, then the heavy half comes from the cache: a pause, a
        // close or a delete takes effect here regardless of what is cached.
        return programDetailCache.load(findPublicProgramById(id).getId());
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProgramResponseDto getPublicProgramByHandle(String handle) {
        Program program = programRepository
                .findByHandle(ProgramHandlePolicy.normalize(handle))
                .filter(this::isPubliclyAccessible)
                .orElseThrow(this::programNotFound);
        // Keyed by id, not by handle: one cached entry per program, so a write
        // has a single key to evict rather than one per way in.
        return programDetailCache.load(program.getId());
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
            UUID organizationId,
            Pageable pageable
    ) {
        Organization organization = findAccessibleOrganization(
                organizationId,
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
            UUID organizationId,
            Pageable pageable
    ) {
        Organization organization = findAccessibleOrganization(
                organizationId,
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

    /**
     * Whether a handle can be taken, answered while somebody is still typing
     * it rather than after four wizard steps.
     *
     * <p>Checks every program regardless of state, visibility or deletion,
     * because that is what {@link #requireUniqueHandle} enforces on the write.
     * An availability check that consulted a narrower set would call a handle
     * free and then watch the save reject it, which is the failure it exists
     * to prevent.
     */
    @Override
    @Transactional(readOnly = true)
    public ProgramHandleAvailabilityResponse checkHandleAvailability(
            String handle,
            UUID excludedProgramId
    ) {
        String normalized = ProgramHandlePolicy.normalize(
                handle == null ? "" : handle
        );

        if (normalized.length() < ProgramHandlePolicy.MIN_LENGTH
                || normalized.length() > ProgramHandlePolicy.MAX_LENGTH) {
            return ProgramHandleAvailabilityResponse.unavailable(
                    normalized,
                    ProgramHandlePolicy.LENGTH_MESSAGE
            );
        }
        if (!ProgramHandlePolicy.isValid(normalized)) {
            return ProgramHandleAvailabilityResponse.unavailable(
                    normalized,
                    ProgramHandlePolicy.FORMAT_MESSAGE
            );
        }

        boolean taken = excludedProgramId == null
                ? programRepository.existsByHandleIgnoreCase(normalized)
                : programRepository.existsByHandleIgnoreCaseAndIdNot(
                        normalized,
                        excludedProgramId
                );

        return taken
                ? ProgramHandleAvailabilityResponse.unavailable(
                        normalized,
                        "Already used by another program"
                )
                : ProgramHandleAvailabilityResponse.available(normalized);
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

    /**
     * @param submit whether to hand the program to administrators in the same
     *               transaction. Creating and then submitting as two calls
     *               leaves a draft the author never meant to keep whenever the
     *               second call fails, and tells them submission failed while
     *               it sits in their list. Equivalent to {@code state=ACTIVE},
     *               which says the same thing less plainly.
     */
    @Override
    @Transactional
    public ProgramResponseDto createProgram(
            UUID organizationId,
            boolean submit,
            ProgramRequestDto request
    ) {
        Organization organization = findAccessibleOrganization(
                organizationId,
                OrganizationPermission.CREATE_PROGRAM
        );
        requireUniqueHandle(request.handle(), null);

        Program program = mapper.toEntity(request);
        program.setOrganizationId(organization.getId());
        // Asking to start ACTIVE is the organization saying the program is
        // finished, so it goes straight to the queue. A draft stays with its
        // author until they submit it. Resolved before the flag is consulted,
        // so an incoherent starting state is still refused when submitting.
        ProgramState requestedState = resolveInitialState(request.state());
        boolean submitNow = submit || requestedState == ProgramState.ACTIVE;
        program.setState(submitNow
                ? ProgramState.ACTIVE
                : ProgramState.DRAFT);
        program.setSubmissionState(submitNow
                ? SubmissionState.PENDING_REVIEW
                : SubmissionState.NOT_SUBMITTED);
        // A draft may be incomplete; submission is where completeness is
        // checked. Validating here too would put the whole published-program
        // contract in front of a client trying to save step one.
        if (submitNow) {
            validateProgramConfiguration(program);
        }

        Program saved = programRepository.saveAndFlush(program);
        ProgramUpdate update = logUpdate(saved,
                submitNow
                        ? "Program created and submitted for admin review; "
                                + "launches on approval"
                        : "Program created as a draft");

        if (submitNow) {
            notifyAdministratorsOfReview(
                    saved,
                    update,
                    "New program awaiting review"
            );
        }

        return mapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
        // An unsubmitted draft is still being written, so saving one step of it
        // must not demand the rest. Anything already handed to an administrator
        // — or live — is held to the full contract on every edit.
        if (!isUnsubmittedDraft(program)) {
            validateProgramConfiguration(program);
        }
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
            moveToPendingReview(program);
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
    public ProgramResponseDto submitProgram(UUID id) {
        Program program = findProgramForManagement(
                id,
                OrganizationPermission.EDIT_PROGRAM
        );
        SubmissionState current = program.getSubmissionState();
        if (program.getState() != ProgramState.DRAFT
                || (current != SubmissionState.NOT_SUBMITTED
                        && current != SubmissionState.REJECTED)) {
            throw conflict(
                    "Only draft or rejected programs can be submitted "
                            + "for review"
            );
        }
        boolean resubmission = current == SubmissionState.REJECTED;

        validateProgramConfiguration(program);
        moveToPendingReview(program);
        program.setVisibility(Visibility.PRIVATE);
        ProgramUpdate update = logUpdate(
                program,
                resubmission
                        ? "Program resubmitted for admin review"
                        : "Program submitted for admin review"
        );

        notifyAdministratorsOfReview(
                program,
                update,
                resubmission
                        ? "Program resubmitted for review"
                        : "New program awaiting review"
        );

        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
     * The moderation counterpart to {@link #deleteProgram(UUID)}: an admin
     * takes a program down without being a member of the organization that
     * owns it, which is what {@code deleteProgram} demands.
     *
     * <p>It also sends the program back to {@link SubmissionState#REJECTED}.
     * The organization keeps its restore button and loses no work, but a
     * restored program comes back as a private draft that has to be
     * resubmitted and re-approved — otherwise the people the removal was
     * aimed at could undo it with one click.
     *
     * <p>Removing an already-removed program is a no-op rather than an error,
     * so resolving two flags on the same program does not fail on the second.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
    public void removeProgramByAdmin(UUID id) {
        requireRole(ADMIN_ROLE);
        Program program = programRepository.findById(id)
                .orElseThrow(this::programNotFound);
        if (program.getDeletedAt() != null) {
            return;
        }

        program.setState(ProgramState.CLOSED);
        program.setVisibility(Visibility.PRIVATE);
        program.setSubmissionState(SubmissionState.REJECTED);
        ProgramUpdate update = logUpdate(program, "Program removed by admin");
        program.setDeletedAt(LocalDateTime.now());

        organizationRepository.findById(program.getOrganizationId())
                .ifPresent(organization -> notifyOrganizationOwner(
                        organization,
                        program,
                        update,
                        "Program removed",
                        "\"" + program.getName()
                                + "\" was removed by an administrator."
                ));
    }

    /**
     * Undoes a delete without republishing. Deleting closes the program and
     * makes it private. Restore normally hands back a private draft the
     * organization must relaunch on purpose; a program already pending review
     * resumes as active so it cannot re-enter the invalid pending/draft pair.
     * Visibility remains private in both cases, so restore never puts a
     * program back on the public internet as a side effect.
     *
     * <p>The admin decision rides along untouched, so a program the owner
     * deleted while it was approved needs no second review — but one an admin
     * removed comes back rejected, and does. A deleted handle stays reserved
     * against {@code existsByHandleIgnoreCase}, so nothing can have claimed it
     * in the meantime.
     */
    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
        program.setState(
                program.getSubmissionState() == SubmissionState.PENDING_REVIEW
                        ? ProgramState.ACTIVE
                        : ProgramState.DRAFT
        );
        program.setVisibility(Visibility.PRIVATE);
        logUpdate(
                program,
                program.getState() == ProgramState.DRAFT
                        ? "Program restored as a private draft"
                        : "Pending program restored as active and private"
        );
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
        // An unsubmitted program has never been handed to the platform. The
        // review queue is not a window onto every organization's unfinished
        // work, so there is no filter that reaches one.
        if (submissionState == SubmissionState.NOT_SUBMITTED) {
            throw badRequest(
                    "Programs that have not been submitted cannot be reviewed"
            );
        }
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
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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

        // Pending programs now enter review as ACTIVE. Promote any legacy row
        // that still has the old DRAFT + PENDING_REVIEW combination as well.
        if (program.getState() == ProgramState.DRAFT) {
            program.setState(ProgramState.ACTIVE);
        }
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
                        + "\" has been approved."
        );

        return mapper.toResponseDto(program);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROGRAM_DETAIL, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.PROGRAM_LISTING, allEntries = true)
    })
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
            UUID organizationId,
            OrganizationPermission permission
    ) {
        return organizationAuthorization.findAccessibleOrganization(
                extractCurrentUserId(),
                organizationId,
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

    /**
     * A program still with its author, free to be incomplete. A rejected
     * program counts: it has come back to be fixed, and holding half a fix to
     * the full contract is the same trap as holding a new draft to it.
     */
    private boolean isUnsubmittedDraft(Program program) {
        return program.getState() == ProgramState.DRAFT
                && (program.getSubmissionState() == SubmissionState.NOT_SUBMITTED
                        || program.getSubmissionState() == SubmissionState.REJECTED);
    }

    private void requireUniqueHandle(String handle, UUID excludedId) {
        String normalizedHandle = ProgramHandlePolicy.normalize(handle);
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

    /**
     * The full published-program contract, checked wherever a program is
     * submitted, approved or live — never on a draft.
     */
    private void validateProgramConfiguration(Program program) {
        if (program.getEngagementType() == null) {
            throw badRequest("Engagement type is required");
        }
        if (program.getVisibility() == null) {
            throw badRequest("Visibility is required");
        }
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
     * <p>The choice also decides whether the program enters review. A
     * {@code DRAFT} is unfinished work that stays {@code NOT_SUBMITTED} until
     * its author calls {@link #submitProgram(UUID)}. Submission promotes it to
     * {@code ACTIVE} and sends it for review; asking for {@code ACTIVE} at
     * creation does both immediately.
     *
     * <p>Creating as {@code ACTIVE} does not skip review: the submission state
     * is still {@code PENDING_REVIEW}, and the public listing requires both
     * {@code ACTIVE} and {@code APPROVED}. It only submits immediately; a
     * program created as a draft reaches the same pair of states when its
     * author submits it later.
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

    /**
     * Hands a program to administrators for review. A program waiting for an
     * admin is no longer an authoring draft, although approval is still
     * required before it can be exposed by the public APIs.
     */
    private void moveToPendingReview(Program program) {
        program.setSubmissionState(SubmissionState.PENDING_REVIEW);
        program.setState(ProgramState.ACTIVE);
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

    private void markPublishedIfPublic(Program program) {
        if (program.getPublishedAt() == null
                && isPubliclyAccessible(program)) {
            program.setPublishedAt(LocalDateTime.now());
        }
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
