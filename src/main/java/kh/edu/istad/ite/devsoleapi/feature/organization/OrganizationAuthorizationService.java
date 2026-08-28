package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationAuthorizationService {

    /**
     * Named on the wire wherever an account has to choose, so the client is
     * told which parameter answers the question rather than being left to
     * guess from a 409.
     */
    public static final String ORGANIZATION_PARAMETER = "organizationId";

    /**
     * The one wording for the selector, so every endpoint that takes it says
     * the same thing in the published spec.
     */
    public static final String ORGANIZATION_PARAMETER_DESCRIPTION =
            "Which organization to act in. Required only for an account that "
                    + "belongs to more than one; omitting it then answers 409 "
                    + "with the candidate ids in errorDetails.organizationIds.";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    /**
     * The organization the caller belongs to, whatever their permissions.
     *
     * <p>Reading a team roster is the case this exists for: being on a team is
     * what entitles someone to see who else is on it, so requiring a
     * permission would mean a Viewer is a member of a company whose members
     * they may not look at.
     *
     * @param organizationId which organization, for an account that belongs to
     *                       more than one. Null asks for the only one there
     *                       is, and is answered with a 409 naming the
     *                       candidates when there is a choice to make.
     */
    public Organization findAccessibleOrganization(
            UUID userId,
            UUID organizationId
    ) {
        return resolve(userId, organizationId, null);
    }

    public Organization findAccessibleOrganization(
            UUID userId,
            UUID organizationId,
            OrganizationPermission permission
    ) {
        return resolve(userId, organizationId, permission);
    }

    private Organization resolve(
            UUID userId,
            UUID organizationId,
            OrganizationPermission permission
    ) {
        Map<UUID, Organization> organizations = accessibleOrganizations(
                userId,
                permission == null
                        ? member -> true
                        : member -> member.hasPermission(permission)
        );

        if (organizationId != null) {
            Organization chosen = organizations.get(organizationId);
            if (chosen == null) {
                throw refuse(permission, organizationId);
            }
            return chosen;
        }
        if (organizations.isEmpty()) {
            throw refuse(permission, null);
        }
        if (organizations.size() > 1) {
            throw ambiguous(organizations.keySet());
        }
        return organizations.values().iterator().next();
    }

    /**
     * Every active organization the caller can act in under the given test,
     * owned first. Ownership is not a membership row and carries every
     * permission, so it is added without consulting the test at all.
     */
    private Map<UUID, Organization> accessibleOrganizations(
            UUID userId,
            Predicate<OrganizationMember> permitted
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

        return organizations;
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
     * Whether two accounts are colleagues at some active organization.
     *
     * <p>Answers what a teammate may be shown of somebody's profile. A roster
     * already shows a member's email to everyone else on the same team, so a
     * profile page that hides it from that same viewer is inconsistent rather
     * than protective.
     */
    public boolean shareOrganization(UUID userId, UUID otherUserId) {
        if (userId == null || otherUserId == null) {
            return false;
        }

        Set<UUID> organizationIds = accessibleOrganizations(
                userId,
                member -> true
        ).keySet();
        if (organizationIds.isEmpty()) {
            return false;
        }

        return accessibleOrganizations(otherUserId, member -> true)
                .keySet()
                .stream()
                .anyMatch(organizationIds::contains);
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

    private RuntimeException refuse(
            OrganizationPermission permission,
            UUID organizationId
    ) {
        String where = organizationId == null
                ? "an active organization"
                : "organization " + organizationId;

        if (permission == null) {
            return new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of " + where
            );
        }
        return missingPermission(
                permission,
                "You do not have " + permission + " permission in " + where
        );
    }

    /**
     * An account at more than one company, on an endpoint written for one.
     *
     * <p>Answering with either organization would be a guess, and answering
     * with the first would quietly hide the other — so the caller is asked,
     * and handed the ids it needs to ask with.
     */
    private DetailedApiException ambiguous(Set<UUID> organizationIds) {
        return new DetailedApiException(
                HttpStatus.CONFLICT,
                "You have access to more than one organization; name the one "
                        + "you mean with the " + ORGANIZATION_PARAMETER
                        + " query parameter",
                Map.of(
                        "parameter", ORGANIZATION_PARAMETER,
                        "organizationIds", organizationIds.stream()
                                .map(UUID::toString)
                                .toList()
                )
        );
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
