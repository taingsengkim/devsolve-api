package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.exception.MissingPermissionException;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationAuthorizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    /**
     * The organization the caller belongs to, whatever their permissions.
     *
     * <p>Reading a team roster is the case this exists for: being on a team is
     * what entitles someone to see who else is on it, so requiring a
     * permission would mean a Viewer is a member of a company whose members
     * they may not look at.
     */
    public Organization findSingleAccessibleOrganization(UUID userId) {
        return findSingleAccessibleOrganization(
                userId,
                member -> true,
                () -> forbidden("You are not a member of an active organization")
        );
    }

    public Organization findSingleAccessibleOrganization(
            UUID userId,
            OrganizationPermission permission
    ) {
        return findSingleAccessibleOrganization(
                userId,
                member -> member.hasPermission(permission),
                () -> missingPermission(
                        permission,
                        "You do not have " + permission
                                + " permission in an active organization"
                )
        );
    }

    private Organization findSingleAccessibleOrganization(
            UUID userId,
            Predicate<OrganizationMember> permitted,
            Supplier<RuntimeException> refusal
    ) {
        Map<UUID, Organization> organizations = new LinkedHashMap<>();

        organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .ifPresent(organization ->
                        organizations.put(organization.getId(), organization)
                );

        memberRepository.findByUserIdAndStatus(
                        userId,
                        MembershipStatus.ACTIVE
                )
                .stream()
                .filter(permitted)
                .map(OrganizationMember::getOrganization)
                .filter(this::isActive)
                .forEach(organization ->
                        organizations.put(organization.getId(), organization)
                );

        if (organizations.isEmpty()) {
            throw refusal.get();
        }
        if (organizations.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The authenticated user has access to multiple organizations"
            );
        }
        return organizations.values().iterator().next();
    }

    public Organization requirePermission(
            UUID organizationId,
            UUID userId,
            OrganizationPermission permission
    ) {
        Organization organization = organizationRepository
                .findById(organizationId)
                .filter(this::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active organization not found"
                ));

        if (organization.getOwner().getId().equals(userId)) {
            return organization;
        }

        boolean permitted = memberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .filter(member ->
                        member.getStatus() == MembershipStatus.ACTIVE
                )
                .filter(member -> member.hasPermission(permission))
                .isPresent();
        if (!permitted) {
            throw missingPermission(
                    permission,
                    "Missing organization permission: " + permission
            );
        }
        return organization;
    }

    public boolean hasPermission(
            UUID organizationId,
            UUID userId,
            OrganizationPermission permission
    ) {
        try {
            requirePermission(organizationId, userId, permission);
            return true;
        } catch (ResourceNotFoundException | ResponseStatusException exception) {
            return false;
        }
    }

    public Set<UUID> findAccessibleOrganizationIds(
            UUID userId,
            OrganizationPermission permission
    ) {
        Set<UUID> organizationIds = memberRepository
                .findByUserIdAndStatus(userId, MembershipStatus.ACTIVE)
                .stream()
                .filter(member -> member.hasPermission(permission))
                .map(OrganizationMember::getOrganization)
                .filter(this::isActive)
                .map(Organization::getId)
                .collect(Collectors.toSet());

        organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId)
                .filter(this::isActive)
                .map(Organization::getId)
                .ifPresent(organizationIds::add);

        return organizationIds;
    }

    /**
     * The people at an organization who should hear about something needing
     * that permission — the inverse of
     * {@link #findAccessibleOrganizationIds}. The owner is always included,
     * as they are there too: ownership is not modelled as a membership row,
     * so a lookup over members alone would silently skip the one person who
     * always has every permission.
     */
    public Set<UUID> findUserIdsWithPermission(
            UUID organizationId,
            OrganizationPermission permission
    ) {
        Set<UUID> userIds = memberRepository
                .findByOrganizationIdAndStatusNot(
                        organizationId,
                        MembershipStatus.REMOVED
                )
                .stream()
                .filter(member -> member.getStatus() == MembershipStatus.ACTIVE)
                .filter(member -> member.hasPermission(permission))
                .map(member -> member.getUser().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        organizationRepository.findById(organizationId)
                .filter(this::isActive)
                .map(organization -> organization.getOwner().getId())
                .ifPresent(userIds::add);

        return userIds;
    }

    private boolean isActive(Organization organization) {
        return organization.getDeletedAt() == null
                && organization.getStatus() == OrganizationStatus.ACTIVE;
    }

    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }

    /**
     * Names the permission on the wire so a client can hide what it may not
     * offer, rather than showing an empty screen it cannot explain.
     */
    private MissingPermissionException missingPermission(
            OrganizationPermission permission,
            String reason
    ) {
        return new MissingPermissionException(permission.name(), reason);
    }
}
