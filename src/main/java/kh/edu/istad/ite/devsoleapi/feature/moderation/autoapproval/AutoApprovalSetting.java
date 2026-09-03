package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Whether the model may publish one kind of post without a moderator.
 *
 * <p>A row rather than a configuration property because an administrator has
 * to be able to turn it off from the admin screen the moment it lets something
 * through — a property would mean a redeploy, and the thing you want to switch
 * off is already publishing.
 *
 * <p>Keyed by the target itself, so there is exactly one row per kind and no
 * way to end up with two rows disagreeing about the same switch.
 */
@Entity
@Table(name = "auto_approval_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AutoApprovalSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, updatable = false, length = 20)
    private AutoApprovalTarget target;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** The administrator who last moved the switch, for the audit trail. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
