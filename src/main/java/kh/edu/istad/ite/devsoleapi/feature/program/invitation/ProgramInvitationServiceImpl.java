package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.InviteToProgramRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramInvitationResponse;
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

/**
 * The guest list for a private program.
 *
 * <p>Separate from {@code ResearcherAccessService}, which holds a researcher's
 * standing with a whole company. A private program is private from that
 * company's own approved researchers as well — otherwise approving somebody for
 * one public program would hand them every unannounced one — so its access is
 * held per program and checked here.
 */
@Service
@RequiredArgsConstructor
public class ProgramInvitationServiceImpl implements ProgramInvitationService {

    private static final String USER_ROLE = "USER";

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "id",
            "status",
            "invitedAt",
            "respondedAt",
            "createdAt",
            "updatedAt"
    );

    private final ProgramInvitationRepository invitationRepository;
    private final ProgramRepository programRepository;
    private final UserProfileRepository userProfileRepository;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final ProgramInvitationMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ProgramInvitationResponse invite(
            UUID programId,
            InviteToProgramRequest request
    ) {
        UUID inviterId = currentUserId();
        Program program = findProgramForManagement(programId, inviterId);
        UserProfile inviter = findUserProfile(inviterId);
        UserProfile researcher = userProfileRepository
                .findByIdAndStatus(request.userId(), UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active researcher profile not found"
                ));

        ProgramInvitation invitation = invitationRepository
                .findByProgram_IdAndResearcher_Id(programId, request.userId())
                .orElse(null);

        // The guards below read the status of a row that already exists. A new
        // one starts at INVITED, so testing it before deciding whether this is
        // a first invitation would refuse every first invitation.
        if (invitation == null) {
            invitation = new ProgramInvitation(program, researcher);
        } else {
            switch (invitation.getStatus()) {
                // Re-inviting somebody who already said yes would reset them to
                // INVITED and quietly take away the access they are using.
                case ACCEPTED -> throw conflict(
                        researcher.getFullName()
                                + " has already accepted an invitation to this "
                                + "program"
                );
                case INVITED -> throw conflict(
                        researcher.getFullName()
                                + " has already been invited and has not "
                                + "answered yet"
                );
                // A no is not the end of it. The company may ask again, and the
                // row is reused rather than piling up one per asking.
                case DECLINED, REVOKED -> { }
            }
        }

        invitation.invite(inviter, trimToNull(request.note()));
        ProgramInvitation saved = invitationRepository.saveAndFlush(invitation);

        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                List.of(researcher.getId()),
                inviterId,
                "Private program invitation",
                "You have been invited to " + program.getName()
                        + ", a private program."
                        + (saved.getNote() == null
                                ? ""
                                : " Note: " + saved.getNote()),
                NotificationType.PROGRAM,
                program.getId(),
                eventKey(saved, "invited")
        ));

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramInvitationResponse> findForProgram(
            UUID programId,
            ProgramInvitationStatus status,
            Pageable pageable
    ) {
        findProgramForManagement(programId, currentUserId());
        return invitationRepository
                .findForProgram(
                        programId,
                        status,
                        PageableValidator.requireAllowedSort(
                                pageable,
                                SORT_PROPERTIES
                        )
                )
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProgramInvitationResponse> findMine(
            ProgramInvitationStatus status,
            Pageable pageable
    ) {
        requireRole(USER_ROLE);
        return invitationRepository
                .findForResearcher(
                        currentUserId(),
                        status,
                        PageableValidator.requireAllowedSort(
                                pageable,
                                SORT_PROPERTIES
                        )
                )
                .map(mapper::toResponse);
    }

    @Override
    @Transactional
    public ProgramInvitationResponse accept(UUID programId) {
        return respond(programId, true);
    }

    @Override
    @Transactional
    public ProgramInvitationResponse decline(UUID programId) {
        return respond(programId, false);
    }

    /**
     * Only an outstanding invitation can be answered.
     *
     * <p>A researcher who has accepted and wants out is not declining — nothing
     * here withdraws an acceptance, because leaving a private program you have
     * already read is not something an API call undoes. A revoked invitation is
     * likewise not answerable: the company has closed it.
     */
    private ProgramInvitationResponse respond(UUID programId, boolean accept) {
        requireRole(USER_ROLE);
        UUID researcherId = currentUserId();
        ProgramInvitation invitation = invitationRepository
                .findByProgram_IdAndResearcher_Id(programId, researcherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "You have not been invited to this program"
                ));

        if (invitation.getStatus() != ProgramInvitationStatus.INVITED) {
            throw conflict(switch (invitation.getStatus()) {
                case ACCEPTED -> "You have already accepted this invitation";
                case DECLINED -> "You have already declined this invitation";
                case REVOKED -> "This invitation has been withdrawn";
                case INVITED -> "";
            });
        }

        if (accept) {
            invitation.accept();
        } else {
            invitation.decline();
        }
        ProgramInvitation saved = invitationRepository.saveAndFlush(invitation);
        Program program = saved.getProgram();

        eventPublisher.publishEvent(new NotificationEvent(
                organizationAuthorization.findUserIdsWithPermission(
                        program.getOrganizationId(),
                        OrganizationPermission.MANAGE_RESEARCHERS
                ),
                accept
                        ? "Private program invitation accepted"
                        : "Private program invitation declined",
                saved.getResearcher().getFullName()
                        + (accept ? " accepted" : " declined")
                        + " your invitation to " + program.getName() + ".",
                NotificationType.PROGRAM,
                program.getId(),
                eventKey(saved, accept ? "accepted" : "declined")
        ));

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProgramInvitationResponse revoke(
            UUID programId,
            UUID researcherId
    ) {
        UUID actorId = currentUserId();
        Program program = findProgramForManagement(programId, actorId);
        ProgramInvitation invitation = invitationRepository
                .findByProgram_IdAndResearcher_Id(programId, researcherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This researcher has not been invited to this program"
                ));

        if (invitation.getStatus() == ProgramInvitationStatus.REVOKED) {
            throw conflict("This invitation has already been withdrawn");
        }

        // Revoking a declined invitation is allowed and means something: it
        // closes the row so a later re-invitation is a deliberate act rather
        // than an accident of the guest list still holding a name.
        invitation.revoke();
        ProgramInvitation saved = invitationRepository.saveAndFlush(invitation);

        eventPublisher.publishEvent(NotificationEvent.toAllExcept(
                List.of(researcherId),
                actorId,
                "Private program access withdrawn",
                "Your access to " + program.getName() + " has been withdrawn.",
                NotificationType.PROGRAM,
                program.getId(),
                eventKey(saved, "revoked")
        ));

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(Program program, UUID userId) {
        if (program.getVisibility() != Visibility.PRIVATE) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        return invitationRepository
                .findByProgram_IdAndResearcher_Id(program.getId(), userId)
                .map(ProgramInvitation::canView)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireAcceptedMember(Program program, UUID researcherId) {
        Optional<ProgramInvitation> invitation = invitationRepository
                .findByProgram_IdAndResearcher_Id(
                        program.getId(),
                        researcherId
                );
        if (invitation.map(ProgramInvitation::canReport).orElse(false)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                switch (invitation
                        .map(ProgramInvitation::getStatus)
                        .orElse(null)) {
                    case INVITED -> "Accept your invitation to "
                            + program.getName()
                            + " before submitting reports to it.";
                    case DECLINED -> "You declined your invitation to "
                            + program.getName() + ".";
                    case REVOKED -> "Your access to " + program.getName()
                            + " has been withdrawn.";
                    // Null, and ACCEPTED which returned above. A researcher who
                    // was never asked is told nothing about the program beyond
                    // that it takes invitations — they got here with an ID they
                    // were not given.
                    case null, default ->
                            "This program is private. Reports can only be "
                                    + "submitted by invited researchers.";
                }
        );
    }

    /**
     * The program, with the caller proven to run it.
     *
     * <p>{@code MANAGE_RESEARCHERS} rather than {@code EDIT_PROGRAM}: this is
     * deciding who may see the program, which is the same judgement as deciding
     * who may report to the company, and not the same as editing its scope.
     */
    private Program findProgramForManagement(UUID programId, UUID actorId) {
        Program program = programRepository.findById(programId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Program not found"
                ));
        organizationAuthorization.requirePermission(
                program.getOrganizationId(),
                actorId,
                OrganizationPermission.MANAGE_RESEARCHERS
        );
        return program;
    }

    private String eventKey(ProgramInvitation invitation, String suffix) {
        return "program-invitation:" + invitation.getId()
                + ":" + invitation.getRevision()
                + ":" + suffix;
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
        } catch (IllegalArgumentException | NullPointerException exception) {
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
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
