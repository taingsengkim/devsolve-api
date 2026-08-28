package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An organization invitation the caller can still accept.
 *
 * <p>Carries the invitation token because acting on it is the whole point:
 * the notification feed cannot, since a notification records that something
 * happened and never learns that the invitation was since accepted, revoked
 * or expired.
 *
 * @param invitedAt when the invitation was last issued — re-inviting after an
 *                  expiry moves this, so it reads as the age of the invitation
 *                  in hand rather than of the first attempt
 */
public record PendingInvitationResponse(
        String invitationToken,
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        String organizationLogoUrl,
        OrgRole role,
        String invitedByName,
        LocalDateTime invitedAt,
        LocalDateTime expiresAt
) {
}
