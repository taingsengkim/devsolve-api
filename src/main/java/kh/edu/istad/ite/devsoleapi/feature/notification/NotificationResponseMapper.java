package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the public notification payload, including display information that
 * belongs to the referenced object rather than the notification row itself.
 */
@Component
@RequiredArgsConstructor
public class NotificationResponseMapper {

    private final CommentRepository commentRepository;
    private final UserProfileRepository userProfileRepository;

    public NotificationResponse toResponse(Notification notification) {
        return toResponses(List.of(notification)).getFirst();
    }

    /**
     * Resolves all active comment authors in two queries for the whole inbox
     * page. Non-comment notifications do not trigger either lookup. Removed
     * comments stay anonymous, matching the comment response contract.
     */
    public List<NotificationResponse> toResponses(
            Collection<Notification> notifications
    ) {
        List<Notification> notificationList = List.copyOf(notifications);
        Map<UUID, UUID> authorIdsByCommentId = findCommentAuthorIds(
                notificationList
        );
        Map<UUID, UserProfile> profilesById = findProfiles(
                authorIdsByCommentId.values()
        );

        return notificationList.stream()
                .map(notification -> toResponse(
                        notification,
                        authorIdsByCommentId,
                        profilesById
                ))
                .toList();
    }

    public Page<NotificationResponse> toPage(Page<Notification> notifications) {
        return new PageImpl<>(
                toResponses(notifications.getContent()),
                notifications.getPageable(),
                notifications.getTotalElements()
        );
    }

    private Map<UUID, UUID> findCommentAuthorIds(
            Collection<Notification> notifications
    ) {
        List<UUID> commentIds = notifications.stream()
                .filter(notification -> notification.getNotifiableType()
                        == NotificationType.COMMENT)
                .map(Notification::getNotifiableId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (commentIds.isEmpty()) {
            return Map.of();
        }

        return commentRepository.findAllById(commentIds)
                .stream()
                .filter(comment -> comment.getDeletedAt() == null)
                .filter(comment -> !comment.isRemoved())
                .collect(Collectors.toMap(
                        Comment::getId,
                        Comment::getAuthorId
                ));
    }

    private Map<UUID, UserProfile> findProfiles(
            Collection<UUID> authorIds
    ) {
        List<UUID> distinctAuthorIds = List.copyOf(
                new LinkedHashSet<>(authorIds)
        );
        if (distinctAuthorIds.isEmpty()) {
            return Map.of();
        }

        return userProfileRepository.findAllById(distinctAuthorIds)
                .stream()
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        Function.identity()
                ));
    }

    private NotificationResponse toResponse(
            Notification notification,
            Map<UUID, UUID> authorIdsByCommentId,
            Map<UUID, UserProfile> profilesById
    ) {
        UUID authorId = notification.getNotifiableType()
                == NotificationType.COMMENT
                ? authorIdsByCommentId.get(notification.getNotifiableId())
                : null;
        UserProfile profile = authorId == null
                ? null
                : profilesById.get(authorId);

        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getNotifiableType(),
                notification.getNotifiableId(),
                authorId,
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
