package kh.edu.istad.ite.devsoleapi.feature.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One person's decision to be emailed, or not, about one kind of notification.
 *
 * <p>Only choices are stored. A user who has never opened their settings has
 * no rows here at all and is served by
 * {@link NotificationType#emailedByDefault()} — which means no backfill when
 * an account is created, and no rows for the many people who never change
 * anything. It also means adding a new {@link NotificationType} takes its
 * default from the enum rather than from whatever a migration guessed.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_preferences_user_type",
                columnNames = {"user_id", "notifiable_type"}
        )
)
public class NotificationPreference extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notifiable_type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    public NotificationPreference(
            UUID userId,
            NotificationType type,
            boolean emailEnabled
    ) {
        this.userId = userId;
        this.type = type;
        this.emailEnabled = emailEnabled;
    }
}
