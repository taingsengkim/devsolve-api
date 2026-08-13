package kh.edu.istad.ite.devsoleapi.feature.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A notification a feature wants delivered once its own work has committed.
 *
 * <p>Services publish this rather than calling {@link NotificationDispatcher}
 * inline. Called inline the dispatcher joins the caller's transaction, so a
 * notification that fails — a dead SSE connection, a constraint, a lost
 * database connection — takes the triage or the comment down with it. Nobody
 * should lose a report state change because we could not tell them about it.
 * {@link NotificationEventListener} picks these up after commit instead, where
 * the business work is already safe.
 *
 * @param eventKey identifies the event, not the delivery. Two publishes
 *                 carrying the same key are the same happening, and the
 *                 recipient is told once — so a retry is harmless. Include
 *                 whatever makes the occurrence distinct: a state, a revision
 *                 number, the comment id.
 */
public record NotificationEvent(
        Collection<UUID> recipientIds,
        String title,
        String content,
        NotificationType type,
        UUID notifiableId,
        String eventKey
) {

    public NotificationEvent {
        // A null recipient means the caller could not resolve somebody —
        // an author who deleted their account, an unassigned owner. Dropping
        // them here keeps every call site from repeating the check, and the
        // set keeps a user who is both author and follower from being told
        // twice.
        Set<UUID> cleaned = new LinkedHashSet<>();
        if (recipientIds != null) {
            for (UUID recipientId : recipientIds) {
                if (recipientId != null) {
                    cleaned.add(recipientId);
                }
            }
        }
        recipientIds = List.copyOf(cleaned);
    }

    public static NotificationEvent to(
            UUID recipientId,
            String title,
            String content,
            NotificationType type,
            UUID notifiableId,
            String eventKey
    ) {
        return new NotificationEvent(
                recipientId == null ? List.of() : List.of(recipientId),
                title,
                content,
                type,
                notifiableId,
                eventKey
        );
    }

    /**
     * Recipients minus the person who caused the event. Nobody wants to be
     * told about their own comment.
     */
    public static NotificationEvent toAllExcept(
            Collection<UUID> recipientIds,
            UUID actorId,
            String title,
            String content,
            NotificationType type,
            UUID notifiableId,
            String eventKey
    ) {
        List<UUID> withoutActor = recipientIds == null
                ? List.of()
                : recipientIds.stream()
                .filter(Objects::nonNull)
                .filter(recipientId -> !recipientId.equals(actorId))
                .toList();

        return new NotificationEvent(
                withoutActor,
                title,
                content,
                type,
                notifiableId,
                eventKey
        );
    }

    public boolean hasRecipients() {
        return !recipientIds.isEmpty();
    }
}
