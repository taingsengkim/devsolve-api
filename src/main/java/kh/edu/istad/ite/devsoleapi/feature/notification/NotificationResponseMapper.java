package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
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
    private final ProblemRepository problemRepository;
    private final SolutionRepository solutionRepository;
    private final ShowCasesRepository showCasesRepository;
    private final UserProfileRepository userProfileRepository;

    public NotificationResponse toResponse(Notification notification) {
        return toResponses(List.of(notification)).getFirst();
    }

    /**
     * Resolves the people behind a whole inbox page in one query per kind of
     * thing on it, plus one for the profiles. A page of nothing but report
     * state changes triggers none of them.
     */
    public List<NotificationResponse> toResponses(
            Collection<Notification> notifications
    ) {
        List<Notification> notificationList = List.copyOf(notifications);
        Map<UUID, UUID> actorIdsByNotifiableId = findActorIds(notificationList);
        Map<UUID, UserProfile> profilesById = findProfiles(
                notificationList.stream()
                        .map(notification -> actorOf(
                                notification,
                                actorIdsByNotifiableId
                        ))
                        .filter(Objects::nonNull)
                        .toList()
        );

        return notificationList.stream()
                .map(notification -> toResponse(
                        notification,
                        actorIdsByNotifiableId,
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

    /**
     * Who each notification is about, by the id it points at.
     *
     * <p>The person is not stored on the row, so it is read back from whatever
     * the notification refers to: the comment that was written, the solution
     * that was posted, the problem or showcase that was published. A follow
     * points at the new follower's profile, so there the notifiable already
     * is the person.
     *
     * <p>The kinds left out have no single person behind them — a report
     * changing state, a payout, an organization decision — and inventing one
     * from the recipient would put their own face on the platform's message
     * to them.
     *
     * <p>Content that has since been deleted stays anonymous, matching the
     * comment response contract: a removed comment keeps its place in a thread
     * without keeping its author's name on it.
     */
    private Map<UUID, UUID> findActorIds(List<Notification> notifications) {
        Map<UUID, UUID> actorIdsByNotifiableId = new LinkedHashMap<>();

        List<UUID> commentIds = notifiableIds(
                notifications,
                NotificationType.COMMENT
        );
        if (!commentIds.isEmpty()) {
            commentRepository.findAllById(commentIds).stream()
                    .filter(comment -> comment.getDeletedAt() == null)
                    .filter(comment -> !comment.isRemoved())
                    .forEach(comment -> actorIdsByNotifiableId.put(
                            comment.getId(),
                            comment.getAuthorId()
                    ));
        }

        List<UUID> solutionIds = notifiableIds(
                notifications,
                NotificationType.SOLUTION
        );
        if (!solutionIds.isEmpty()) {
            solutionRepository.findAllById(solutionIds)
                    .forEach(solution -> actorIdsByNotifiableId.put(
                            solution.getId(),
                            solution.getAuthorId()
                    ));
        }

        List<UUID> problemIds = notifiableIds(
                notifications,
                NotificationType.PROBLEM
        );
        if (!problemIds.isEmpty()) {
            problemRepository.findAllById(problemIds)
                    .forEach(problem -> actorIdsByNotifiableId.put(
                            problem.getId(),
                            problem.getAuthorId()
                    ));
        }

        List<UUID> showcaseIds = notifiableIds(
                notifications,
                NotificationType.SHOWCASE
        );
        if (!showcaseIds.isEmpty()) {
            showCasesRepository.findAllById(showcaseIds).stream()
                    .filter(showcase -> showcase.getDeletedAt() == null)
                    .filter(showcase -> showcase.getAuthor() != null)
                    .forEach(showcase -> actorIdsByNotifiableId.put(
                            showcase.getId(),
                            showcase.getAuthor().getId()
                    ));
        }

        notifiableIds(notifications, NotificationType.USER)
                .forEach(profileId -> actorIdsByNotifiableId.put(
                        profileId,
                        profileId
                ));

        return actorIdsByNotifiableId;
    }

    private List<UUID> notifiableIds(
            List<Notification> notifications,
            NotificationType type
    ) {
        return notifications.stream()
                .filter(notification -> notification.getNotifiableType() == type)
                .map(Notification::getNotifiableId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * The person to show beside one notification, or null where there is none
     * to show.
     *
     * <p>An actor who is the recipient means the platform acted on their own
     * work — "your problem was published" — which is a message from us, not
     * from them. Stamping the reader's own avatar on it reads as though they
     * had done it to themselves.
     */
    private UUID actorOf(
            Notification notification,
            Map<UUID, UUID> actorIdsByNotifiableId
    ) {
        UUID actorId = actorIdsByNotifiableId.get(
                notification.getNotifiableId()
        );
        return actorId == null || actorId.equals(notification.getUserId())
                ? null
                : actorId;
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
            Map<UUID, UUID> actorIdsByNotifiableId,
            Map<UUID, UserProfile> profilesById
    ) {
        UUID actorId = actorOf(notification, actorIdsByNotifiableId);
        UserProfile profile = actorId == null
                ? null
                : profilesById.get(actorId);

        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getNotifiableType(),
                notification.getNotifiableId(),
                actorId,
                profile == null ? null : profile.getUsername(),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
