package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import jakarta.persistence.*;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organization_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "user_id"}))
public class OrganizationMember extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "membership_status_enum")
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @ManyToOne
    @JoinColumn(name = "invited_by")
    private UserProfile invitedBy;

    @Column(name = "invitation_email", length = 255)
    private String invitationEmail;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 20
    )
    private OrgRole role = OrgRole.MEMBER;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "organization_member_permissions",
            joinColumns = @JoinColumn(name = "organization_member_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private Set<OrganizationPermission> permissions = new HashSet<>();

    @Column(name = "invitation_token", unique = true, length = 36)
    private String invitationToken;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    public OrganizationMember(Organization organization, UserProfile user, OrgRole role) {
        this.organization = organization;
        this.user = user;
        this.role = role;
        setPermissions(OrganizationPermission.defaultsFor(role));
    }

    public void accept() {
        this.status = MembershipStatus.ACTIVE;
        this.joinedAt = LocalDateTime.now();
        this.invitationToken = null;
    }

    public boolean isInvitationPending() {
        return status == MembershipStatus.SUSPENDED && invitationToken != null;
    }

    public void markAsRemoved() {
        this.status = MembershipStatus.REMOVED;
        this.invitationToken = null;
    }

    public void applyRoleDefaults() {
        setPermissions(OrganizationPermission.defaultsFor(role));
    }

    public void setPermissions(Set<OrganizationPermission> permissions) {
        this.permissions.clear();
        if (permissions != null) {
            this.permissions.addAll(permissions);
        }
    }

    public boolean hasPermission(OrganizationPermission permission) {
        return permissions.contains(permission);
    }
}
