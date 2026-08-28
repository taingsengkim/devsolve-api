package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A team invitation that still has to reach the invited person's inbox.
 *
 * <p>Carries plain values only, never entities: {@link
 * OrganizationInvitationMailer} handles it on another thread once the
 * invitation has committed, where a lazy association would have nothing left
 * to load from.
 *
 * @param invitationToken the token the invited user posts back to accept.
 *                        Anyone holding it can accept as the invited user
 *                        only — {@code acceptInvitation} still checks the
 *                        caller's identity — but it stays out of logs all the
 *                        same.
 */
public record OrganizationInvitationEmailEvent(
        UUID organizationId,
        String organizationName,
        String inviterName,
        String recipientEmail,
        String recipientName,
        OrgRole role,
        String invitationToken,
        LocalDateTime expiresAt
) {
}
