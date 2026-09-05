package kh.edu.istad.ite.devsoleapi.feature.moderation.takedown;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationAction;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionMapper;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionRepository;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationActionType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesService;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentTakedownServiceImpl implements ContentTakedownService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final ProblemService problemService;
    private final ShowCasesService showCasesService;
    private final SolutionService solutionService;
    private final CommentService commentService;
    private final ProgramService programService;
    private final UserProfileRepository userProfileRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ModerationActionMapper moderationActionMapper;

    @Override
    @Transactional
    public ModerationActionResponse takeDown(
            ModerationTargetType targetType,
            UUID targetId,
            String reason
    ) {
        requireAdmin();
        UUID adminId = currentUserId();
        UserProfile admin = userProfileRepository.findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found"
                ));

        remove(targetType, targetId, adminId);

        // Written after the removal, so a refusal deeper in the content's own
        // path leaves no record of a takedown that never happened.
        ModerationAction action = new ModerationAction();
        action.setAdmin(admin);
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setAction(ModerationActionType.REMOVE);
        action.setReason(reason == null ? null : reason.trim());

        return moderationActionMapper
                .mapModerationActionToModerationActionResponse(
                        moderationActionRepository.save(action)
                );
    }

    /**
     * Hands off to the removal each type already owns.
     *
     * <p>Deliberately not reimplemented here. Each of these knows something
     * this class should not have to: deleting a problem hides its solutions in
     * the same transaction and discards its attachments, removing a comment
     * that has replies tombstones it rather than erasing writing its author
     * never consented to losing, and removing a program makes it private as
     * well as deleted so it leaves the listing. A second copy of any of that
     * would drift.
     *
     * <p>The problem and solution paths admit an administrator on their own
     * (both check the ADMIN role beside the author check), so they need no
     * separate entry point. The showcase one did not, which is why
     * {@link ShowCasesService#removeByAdmin} exists.
     */
    private void remove(
            ModerationTargetType targetType,
            UUID targetId,
            UUID adminId
    ) {
        switch (targetType) {
            case PROBLEM -> problemService.softDelete(targetId);
            case SHOWCASE -> showCasesService.removeByAdmin(targetId);
            case SOLUTION -> solutionService.deleteSolution(targetId);
            case COMMENT -> commentService.removeByModerator(targetId, adminId);
            case PROGRAM -> programService.removeProgramByAdmin(targetId);

            // An account is suspended, removed or banned through
            // ModerationService, which also has to reach Keycloak; routing it
            // through here would leave a login enabled behind a removed
            // profile.
            case USER -> throw badRequest(
                    "An account is moderated through "
                            + "POST /api/v1/admin/{userId}/moderation-actions, "
                            + "not taken down as content"
            );

            // A report is one half of a disclosure between a researcher and a
            // company, and the timeline around it — disputes, retests,
            // payouts — reads as evidence. Removing one is not a moderation
            // act this platform offers.
            case REPORT -> throw badRequest(
                    "A report cannot be taken down"
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

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can take content down"
            );
        }
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
