package kh.edu.istad.ite.devsoleapi.feature.organization.researcher;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.InviteResearcherRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReportingEligibilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.RequestResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ResearcherAccessResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.dto.ReviewResearcherAccessRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessDecision;
import kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums.ResearcherAccessStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResearcherAccessServiceImpl implements ResearcherAccessService {

    private static final String USER_ROLE = "USER";

    private static final Set<String> ACCESS_SORT_PROPERTIES = Set.of(
            "id",
            "status",
            "requestedAt",
            "reviewedAt",
            "createdAt",
            "updatedAt"
    );

    private final OrganizationResearcherRepository researcherAccessRepository;
    private final OrganizationRepository organizationRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProgramRepository programRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final ResearcherAccessMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ResearcherAccessResponse request(
            UUID organizationId,
            RequestResearcherAccessRequest request
    ) {
        requireRole(USER_ROLE);
        UUID researcherId = currentUserId();
        Organization organization = findActiveOrganization(organizationId);
        UserProfile researcher = findUserProfile(researcherId);
        String motivation = request.motivation().trim();

        OrganizationResearcher access = researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        researcherId
                )
                .orElse(null);

        if (access == null) {
            access = new OrganizationResearcher(organization, researcher);
        } else {
            switch (access.getStatus()) {
                case PENDING -> throw conflict(
                        "Your access request to " + organization.getName()
                                + " is already waiting to be reviewed"
                );
                case APPROVED -> throw conflict(
                        organization.getName()
                                + " has already approved you. You can submit "
                                + "reports to their programs now."
                );
                // A refusal is not the end of it: the researcher may ask again.
                case REJECTED, REVOKED -> { }
            }
        }
        access.markRequested(motivation);

        OrganizationResearcher saved =
                researcherAccessRepository.saveAndFlush(access);

        eventPublisher.publishEvent(new NotificationEvent(
                organizationAuthorization.findUserIdsWithPermission(
                        organizationId,
                        OrganizationPermission.MANAGE_RESEARCHERS
                ),
                "New researcher access request",
                researcher.getFullName()
                        + " is asking to be approved to submit reports to "
                        + organization.getName() + "'s programs.",
                NotificationType.ORGANIZATION,
                organizationId,
                eventKey(saved, "requested")
        ));

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ResearcherAccessResponse invite(
            UUID organizationId,
            InviteResearcherRequest request
    ) {
        UUID reviewerId = currentUserId();
        Organization organization =
                organizationAuthorization.requirePermission(
                        organizationId,
                        reviewerId,
                        OrganizationPermission.MANAGE_RESEARCHERS
                );
        UserProfile reviewer = findUserProfile(reviewerId);
        UserProfile researcher = userProfileRepository
                .findByIdAndStatus(request.userId(), UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active researcher profile not found"
                ));
        String note = trimToNull(request.note());

        OrganizationResearcher access = researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        request.userId()
                )
                .orElse(null);

        if (access == null) {
            access = new OrganizationResearcher(organization, researcher);
        } else if (access.isApproved()) {
            throw conflict(
                    researcher.getFullName()
                            + " is already approved for this organization"
            );
        }
        access.approve(reviewer, note);

        OrganizationResearcher saved =
                researcherAccessRepository.saveAndFlush(access);
        notifyResearcherOfDecision(saved, organization, reviewerId, true);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ResearcherAccessResponse review(
            UUID organizationId,
            UUID researcherId,
            ReviewResearcherAccessRequest request
    ) {
        UUID reviewerId = currentUserId();
        Organization organization =
                organizationAuthorization.requirePermission(
                        organizationId,
                        reviewerId,
                        OrganizationPermission.MANAGE_RESEARCHERS
                );
        UserProfile reviewer = findUserProfile(reviewerId);
        OrganizationResearcher access = researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        researcherId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This researcher has not requested access to your "
                                + "organization"
                ));

        String note = trimToNull(request.note());
        ResearcherAccessDecision decision = request.decision();
        requireLegalDecision(access.getStatus(), decision);

        switch (decision) {
            case APPROVE -> access.approve(reviewer, note);
            case REJECT -> access.reject(reviewer, note);
            case REVOKE -> access.revoke(reviewer, note);
        }

        OrganizationResearcher saved =
                researcherAccessRepository.saveAndFlush(access);
        notifyResearcherOfDecision(
                saved,
                organization,
                reviewerId,
                decision == ResearcherAccessDecision.APPROVE
        );
        return mapper.toResponse(saved);
    }

    private void requireLegalDecision(
            ResearcherAccessStatus current,
            ResearcherAccessDecision decision
    ) {
        switch (decision) {
            case APPROVE -> {
                if (current == ResearcherAccessStatus.APPROVED) {
                    throw conflict(
                            "This researcher is already approved"
                    );
                }
            }
            case REJECT -> {
                if (current == ResearcherAccessStatus.APPROVED) {
                    throw conflict(
                            "This researcher is already approved. Use REVOKE "
                                    + "to withdraw their access."
                    );
                }
                if (current == ResearcherAccessStatus.REJECTED) {
                    throw conflict(
                            "This request has already been rejected"
                    );
                }
                if (current == ResearcherAccessStatus.REVOKED) {
                    throw conflict(
                            "This researcher's access has already been revoked"
                    );
                }
            }
            case REVOKE -> {
                if (current != ResearcherAccessStatus.APPROVED) {
                    throw conflict(
                            "Only an approved researcher's access can be "
                                    + "revoked"
                    );
                }
            }
        }
    }

    private void notifyResearcherOfDecision(
            OrganizationResearcher access,
            Organization organization,
            UUID actorId,
            boolean approved
    ) {
        String title = approved
                ? "Researcher access approved"
                : access.getStatus() == ResearcherAccessStatus.REVOKED
                        ? "Researcher access revoked"
                        : "Researcher access declined";

        String content = approved
                ? organization.getName()
                        + " approved you. You can now submit reports to their "
                        + "programs."
                : access.getStatus() == ResearcherAccessStatus.REVOKED
                        ? organization.getName()
                                + " has withdrawn your access to their "
                                + "programs."
                        : organization.getName()
                                + " declined your access request.";

        if (access.getReviewNote() != null) {
            content = content + " Note: " + access.getReviewNote();
        }

        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                List.of(access.getResearcher().getId()),
                actorId,
                title,
                content,
                NotificationType.ORGANIZATION,
                organization.getId(),
                eventKey(access, access.getStatus().databaseValue())
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResearcherAccessResponse> findForOrganization(
            UUID organizationId,
            ResearcherAccessStatus status,
            Pageable pageable
    ) {
        organizationAuthorization.requirePermission(
                organizationId,
                currentUserId(),
                OrganizationPermission.MANAGE_RESEARCHERS
        );
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                ACCESS_SORT_PROPERTIES
        );
        Page<OrganizationResearcher> page = status == null
                ? researcherAccessRepository.findByOrganizationId(
                        organizationId,
                        validated
                )
                : researcherAccessRepository.findByOrganizationIdAndStatus(
                        organizationId,
                        status,
                        validated
                );
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResearcherAccessResponse> findMine(
            ResearcherAccessStatus status,
            Pageable pageable
    ) {
        requireRole(USER_ROLE);
        UUID researcherId = currentUserId();
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                ACCESS_SORT_PROPERTIES
        );
        Page<OrganizationResearcher> page = status == null
                ? researcherAccessRepository.findByResearcherId(
                        researcherId,
                        validated
                )
                : researcherAccessRepository.findByResearcherIdAndStatus(
                        researcherId,
                        status,
                        validated
                );
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ResearcherAccessResponse findMineForOrganization(
            UUID organizationId
    ) {
        requireRole(USER_ROLE);
        return researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        currentUserId()
                )
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not requested access to this organization"
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportingEligibilityResponse checkProgramEligibility(
            UUID programId
    ) {
        requireRole(USER_ROLE);
        Program program = programRepository.findById(programId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program not found"
                ));
        UUID organizationId = program.getOrganizationId();
        String organizationName = organizationRepository
                .findById(organizationId)
                .map(Organization::getName)
                .orElse("this organization");

        ResearcherAccessStatus status = researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        currentUserId()
                )
                .map(OrganizationResearcher::getStatus)
                .orElse(null);

        boolean approved = status == ResearcherAccessStatus.APPROVED;
        boolean programAccepting = program.getState() == ProgramState.ACTIVE
                && program.getSubmissionState() == SubmissionState.APPROVED;

        // Access is reported ahead of program state: being unapproved is the
        // thing the researcher has to act on either way.
        String reason;
        if (!approved) {
            reason = describeMissingAccess(status, organizationName, null);
        } else if (!programAccepting) {
            reason = "This program is not currently accepting reports.";
        } else {
            reason = organizationName
                    + " has approved you. You can submit reports to this "
                    + "program.";
        }

        return new ReportingEligibilityResponse(
                programId,
                organizationId,
                organizationName,
                approved && programAccepting,
                status,
                reason
        );
    }

    @Override
    @Transactional(readOnly = true)
    public void requireApprovedReporter(
            UUID organizationId,
            UUID researcherId
    ) {
        Optional<OrganizationResearcher> access = researcherAccessRepository
                .findByOrganizationIdAndResearcherId(
                        organizationId,
                        researcherId
                );
        if (access.map(OrganizationResearcher::isApproved).orElse(false)) {
            return;
        }

        String organizationName = access
                .map(record -> record.getOrganization().getName())
                .orElseGet(() -> organizationRepository
                        .findById(organizationId)
                        .map(Organization::getName)
                        .orElse("This organization"));

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                describeMissingAccess(
                        access.map(OrganizationResearcher::getStatus)
                                .orElse(null),
                        organizationName,
                        access.map(OrganizationResearcher::getReviewNote)
                                .orElse(null)
                )
        );
    }

    private String describeMissingAccess(
            ResearcherAccessStatus status,
            String organizationName,
            String reviewNote
    ) {
        String base;
        if (status == null) {
            base = organizationName
                    + " approves researchers before accepting reports. "
                    + "Request access, and you can submit once they approve "
                    + "you.";
        } else {
            base = switch (status) {
                case PENDING -> organizationName
                        + " has not reviewed your access request yet. You can "
                        + "submit once they approve it.";
                case REJECTED -> organizationName
                        + " declined your access request.";
                case REVOKED -> organizationName
                        + " has withdrawn your access to their programs.";
                // Unreachable: both callers check for approval first.
                case APPROVED -> organizationName + " has approved you.";
            };
        }
        return reviewNote == null ? base : base + " Note: " + reviewNote;
    }

    private String eventKey(OrganizationResearcher access, String suffix) {
        return "researcher-access:" + access.getId()
                + ":" + access.getRevision()
                + ":" + suffix;
    }

    private Organization findActiveOrganization(UUID organizationId) {
        return organizationRepository
                .findByIdAndStatusAndDeletedAtIsNull(
                        organizationId,
                        OrganizationStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active organization not found"
                ));
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user profile not found"
                ));
    }

    private UUID currentUserId() {
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

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + role
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
