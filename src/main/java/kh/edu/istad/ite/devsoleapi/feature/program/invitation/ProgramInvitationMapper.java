package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.springframework.stereotype.Component;

/**
 * Hand-written rather than generated, because the response flattens two lazy
 * associations and one of them may legitimately be absent — an invitation whose
 * inviter has since left the company keeps the invitation.
 */
@Component
public class ProgramInvitationMapper {

    public ProgramInvitationResponse toResponse(ProgramInvitation invitation) {
        Program program = invitation.getProgram();
        UserProfile researcher = invitation.getResearcher();
        UserProfile invitedBy = invitation.getInvitedBy();

        return new ProgramInvitationResponse(
                invitation.getId(),
                program.getId(),
                program.getHandle(),
                program.getName(),
                program.getOrganizationId(),
                invitation.getStatus(),
                researcher.getId(),
                researcher.getFullName(),
                researcher.getAvatarUrl(),
                invitedBy == null ? null : invitedBy.getId(),
                invitation.getNote(),
                invitation.getInvitedAt(),
                invitation.getRespondedAt(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt()
        );
    }
}
