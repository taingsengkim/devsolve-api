package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.DisputeMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.DisputeResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ResolveDisputeRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Dispute;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Settles severity disputes.
 *
 * <p>A report whose reported and triage severities disagree is frozen: it
 * cannot be re-triaged, resolved, or paid. Only a platform administrator can
 * break that tie, which is what this service exists to let them do.
 */
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * The administrators' queue: disputes that have reached them and are still
     * waiting on a ruling.
     *
     * <p>Deliberately excludes {@code AWAITING_REPORTER}. That one is a
     * disagreement the reporter has not answered yet, and it is not an
     * administrator's to settle until they refuse it — stepping in earlier
     * would decide, on the researcher's behalf, that there was an argument.
     */
    private static final Set<DisputeStatus> ACTIVE_DISPUTE_STATUSES =
            EnumSet.of(
                    DisputeStatus.OPEN,
                    DisputeStatus.UNDER_REVIEW
            );

    private static final Set<String> DISPUTE_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "resolvedAt",
            "status"
    );

    private final DisputeRepository disputeRepository;
    private final ReportRepository reportRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final DisputeMapper disputeMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<DisputeResponse> findForAdmin(
            DisputeStatus status,
            UUID programId,
            UUID reportId,
            boolean pendingOnly,
            Pageable pageable
    ) {
        requireAdmin();

        Specification<Dispute> specification =
                DisputeSpecification.forProgram(programId)
                        .and(DisputeSpecification.forReport(reportId));
        // pendingOnly is the queue view — everything still waiting on a
        // decision — and an explicit status narrows it further.
        specification = pendingOnly
                ? specification.and(DisputeSpecification.withStatusIn(
                        ACTIVE_DISPUTE_STATUSES
                ))
                : specification.and(DisputeSpecification.withStatus(status));

        Pageable validatedPageable = PageableValidator.requireAllowedSort(
                pageable,
                DISPUTE_SORT_PROPERTIES
        );
        return disputeRepository.findAll(specification, validatedPageable)
                .map(disputeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeResponse findById(UUID id) {
        requireAdmin();
        return disputeMapper.toResponse(findDispute(id));
    }

    @Override
    @Transactional
    public DisputeResponse resolve(
            UUID id,
            ResolveDisputeRequest request
    ) {
        requireAdmin();
        Dispute dispute = findDispute(id);

        if (dispute.getStatus() == DisputeStatus.AWAITING_REPORTER) {
            throw conflict(
                    "The reporter has not yet answered this severity "
                            + "disagreement"
            );
        }
        if (!ACTIVE_DISPUTE_STATUSES.contains(dispute.getStatus())) {
            throw conflict("This dispute has already been settled");
        }

        return switch (request.status()) {
            case UNDER_REVIEW -> claim(dispute, request);
            case RESOLVED -> settle(dispute, request);
            case DISMISSED -> dismiss(dispute, request);
            case OPEN -> throw badRequest(
                    "A dispute cannot be moved back to OPEN"
            );
            // Only the platform puts a dispute here, when triage and the
            // reporter first disagree. An administrator ruling is the step
            // after that one, never a way back into it.
            case AWAITING_REPORTER -> throw badRequest(
                    "A dispute cannot be sent back to the reporter"
            );
        };
    }

    /**
     * Claiming the dispute without deciding it. Nothing about the report
     * changes: it stays frozen, but both sides can see somebody has picked
     * it up.
     */
    private DisputeResponse claim(
            Dispute dispute,
            ResolveDisputeRequest request
    ) {
        if (request.finalSeverity() != null) {
            throw badRequest(
                    "A final severity cannot be set while a dispute is only "
                            + "under review"
            );
        }
        if (dispute.getStatus() == DisputeStatus.UNDER_REVIEW) {
            throw conflict("This dispute is already under review");
        }

        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setResolutionNotes(trimToNull(request.resolutionNotes()));
        disputeRepository.saveAndFlush(dispute);

        notifyParties(
                dispute,
                "Severity dispute under review",
                "An administrator is reviewing the severity of \""
                        + dispute.getReport().getTitle() + "\".",
                "under-review"
        );
        return disputeMapper.toResponse(dispute);
    }

    /**
     * The administrator picks the final severity. It need not be either side's
     * claim — the whole point of mediation is that a third number can be the
     * right one.
     */
    private DisputeResponse settle(
            Dispute dispute,
            ResolveDisputeRequest request
    ) {
        if (request.finalSeverity() == null) {
            throw badRequest(
                    "A final severity is required to resolve a dispute"
            );
        }
        if (request.finalSeverity() == Severity.NONE) {
            throw badRequest(
                    "Final severity must be LOW, MEDIUM, HIGH, or CRITICAL. "
                            + "Dismiss the dispute instead to let the triage "
                            + "assessment stand"
            );
        }
        String notes = requireNotes(request);

        applyRuling(
                dispute,
                DisputeStatus.RESOLVED,
                request.finalSeverity(),
                notes
        );

        notifyParties(
                dispute,
                "Severity dispute resolved",
                "An administrator set the severity of \""
                        + dispute.getReport().getTitle() + "\" to "
                        + request.finalSeverity() + ".",
                "resolved:" + request.finalSeverity()
        );
        return disputeMapper.toResponse(dispute);
    }

    /**
     * The triage assessment stands. Recorded as the ruled severity rather than
     * left blank, so the report unfreezes on the company's number and nothing
     * downstream has to re-derive which side won.
     */
    private DisputeResponse dismiss(
            Dispute dispute,
            ResolveDisputeRequest request
    ) {
        if (request.finalSeverity() != null) {
            throw badRequest(
                    "A dismissal keeps the triage severity, so no final "
                            + "severity can be given. Resolve the dispute "
                            + "instead to set one"
            );
        }
        Severity triageSeverity = dispute.getReport().getTriageSeverity();
        if (triageSeverity == null) {
            throw conflict(
                    "This report has no triage severity to fall back on"
            );
        }
        String notes = requireNotes(request);

        applyRuling(
                dispute,
                DisputeStatus.DISMISSED,
                triageSeverity,
                notes
        );

        notifyParties(
                dispute,
                "Severity dispute dismissed",
                "An administrator let the triage severity of \""
                        + dispute.getReport().getTitle() + "\" stand at "
                        + triageSeverity + ".",
                "dismissed:" + triageSeverity
        );
        return disputeMapper.toResponse(dispute);
    }

    /**
     * Writes the decision onto both the dispute and the report it froze. The
     * report's own severity is set here rather than left to triage, because the
     * report has to be payable the moment the ruling lands.
     */
    private void applyRuling(
            Dispute dispute,
            DisputeStatus outcome,
            Severity finalSeverity,
            String notes
    ) {
        UserProfile admin = findUserProfile(currentUserId());

        dispute.setStatus(outcome);
        dispute.setResolvedSeverity(finalSeverity);
        dispute.setResolvedBy(admin);
        dispute.setResolutionNotes(notes);
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.saveAndFlush(dispute);

        Report report = dispute.getReport();
        report.setSeverity(finalSeverity);
        reportRepository.saveAndFlush(report);
    }

    private String requireNotes(ResolveDisputeRequest request) {
        String notes = trimToNull(request.resolutionNotes());
        if (notes == null) {
            throw badRequest(
                    "Resolution notes are required so both sides can see why "
                            + "the dispute was decided this way"
            );
        }
        return notes;
    }

    /**
     * Both sides of the argument hear the outcome: the reporter who raised the
     * severity and everyone at the company who could have triaged it.
     */
    private void notifyParties(
            Dispute dispute,
            String title,
            String content,
            String eventKeySuffix
    ) {
        Report report = dispute.getReport();
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(report.getReporter().getId());
        recipients.addAll(
                organizationAuthorization.findUserIdsWithPermission(
                        report.getProgram().getOrganizationId(),
                        OrganizationPermission.TRIAGE_REPORTS
                )
        );

        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                recipients,
                currentUserId(),
                title,
                content,
                NotificationType.DISPUTE,
                report.getId(),
                "dispute:" + dispute.getId() + ":" + eventKeySuffix
        ));
    }

    private Dispute findDispute(UUID id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dispute not found"
                ));
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user profile not found"
                ));
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + ADMIN_ROLE
            );
        }
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
