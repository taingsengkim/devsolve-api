package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import java.time.LocalDateTime;

public record InvitationResponse(
        MemberResponse member,
        String invitationToken,
        LocalDateTime expiresAt
) {
}
