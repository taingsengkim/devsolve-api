package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final long INVITATION_VALID_DAYS = 7;

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationUserProfileRepository userProfileRepository;
    private final OrganizationMapper organizationMapper;
    private final WebsiteUrlService websiteUrlService;
    private final CompanyIdentityService companyIdentityService;

    @Override
    @Transactional
    public OrganizationResponse register(OrganizationRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Passwords do not match"
            );
        }
        if (!websiteUrlService.matchesEmailDomain(
                request.email(),
                request.companyWebsite()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Business email domain must match the company website domain"
            );
        }

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (userProfileRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Business email is already registered"
            );
        }

        String slug = generateUniqueSlug(request.companyName());
        RegisteredCompany registeredCompany = companyIdentityService.register(
                normalizedEmail,
                request.fullName(),
                request.password()
        );
        AtomicBoolean identityDeleted = new AtomicBoolean(false);
        registerIdentityRollbackCleanup(
                registeredCompany,
                identityDeleted
        );

        try {
            UserProfile owner = new UserProfile();
            owner.setId(registeredCompany.id());
            owner.setEmail(registeredCompany.email());
            owner.setFullName(registeredCompany.fullName());
            owner.setStatus(UserStatus.ACTIVE);
            owner = userProfileRepository.saveAndFlush(owner);

            Organization organization = organizationMapper.toOrganization(
                    request,
                    owner,
                    slug
            );
            organization.setStatus(OrganizationStatus.PENDING);

            return organizationMapper.toOrganizationResponse(
                    organizationRepository.saveAndFlush(organization)
            );
        } catch (RuntimeException exception) {
            deleteCompanyIdentity(registeredCompany, identityDeleted);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse me() {
        return organizationMapper.toOrganizationResponse(findMyOrganization());
    }

    @Override
    @Transactional
    public OrganizationResponse updateMe(OrganizationUpdateRequest request) {
        Jwt jwt = getCurrentJwt();
        Organization organization = findMyOrganization(extractCurrentUserId(jwt));

        organizationMapper.updateOrganization(request, organization);
        if (request.websiteUrl() != null) {
            String email = requireEmail(jwt);
            if (!websiteUrlService.matchesEmailDomain(email, request.websiteUrl())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Business email domain must match the organization website domain"
                );
            }

            String normalizedWebsiteUrl = websiteUrlService.normalize(request.websiteUrl());
            if (!normalizedWebsiteUrl.equals(organization.getWebsiteUrl())) {
                organization.setWebsiteUrl(normalizedWebsiteUrl);
                organization.setStatus(OrganizationStatus.PENDING);
                organization.setVerifiedAt(null);
            }
        }

        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional
    public void deleteMe() {
        Organization organization = findMyOrganization();
        organization.setDeletedAt(LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getById(UUID id) {
        Organization organization = organizationRepository
                .findByIdAndStatusAndDeletedAtIsNull(id, OrganizationStatus.ACTIVE)
                .orElseThrow(() -> organizationNotFound());
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getBySlug(String slug) {
        Organization organization = organizationRepository
                .findBySlugAndStatusAndDeletedAtIsNull(slug, OrganizationStatus.ACTIVE)
                .orElseThrow(() -> organizationNotFound());
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMyMembers() {
        Organization organization = findMyOrganization();
        return memberRepository
                .findByOrganizationIdAndStatusNot(
                        organization.getId(),
                        MembershipStatus.REMOVED
                )
                .stream()
                .map(organizationMapper::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional
    public InvitationResponse inviteMember(InviteMemberRequest request) {
        Organization organization = findMyOrganization();
        UserProfile invitedBy = organization.getOwner();
        UserProfile invitedUser = userProfileRepository
                .findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The invited user must have a DevSolve account"
                ));

        if (invitedUser.getId().equals(invitedBy.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The organization owner cannot be invited as a member"
            );
        }

        Optional<OrganizationMember> existingMember = memberRepository
                .findByOrganizationIdAndUserId(
                        organization.getId(),
                        invitedUser.getId()
                );

        if (existingMember.isPresent()) {
            OrganizationMember member = existingMember.get();
            if (member.getStatus() == MembershipStatus.ACTIVE) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "User is already an active organization member"
                );
            }
            if (member.isInvitationPending() && !isInvitationExpired(member)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A valid invitation has already been sent to this user"
                );
            }
        }

        OrganizationMember member = existingMember.orElseGet(
                () -> new OrganizationMember(
                        organization,
                        invitedUser,
                        request.role()
                )
        );
        member.setRole(request.role());
        member.setStatus(MembershipStatus.SUSPENDED);
        member.setInvitedBy(invitedBy);
        member.setInvitationEmail(invitedUser.getEmail());
        member.setInvitationToken(UUID.randomUUID().toString());
        member.setJoinedAt(null);

        OrganizationMember savedMember = memberRepository.saveAndFlush(member);
        LocalDateTime invitationDate = savedMember.getUpdatedAt() != null
                ? savedMember.getUpdatedAt()
                : LocalDateTime.now();

        return new InvitationResponse(
                organizationMapper.toMemberResponse(savedMember),
                savedMember.getInvitationToken(),
                invitationDate.plusDays(INVITATION_VALID_DAYS)
        );
    }

    @Override
    @Transactional
    public MemberResponse updateMemberRole(
            UUID targetUserId,
            UpdateMemberRoleRequest request
    ) {
        Organization organization = findMyOrganization();
        OrganizationMember member = findMembership(
                organization.getId(),
                targetUserId
        );

        if (member.getStatus() == MembershipStatus.REMOVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A removed membership cannot be updated"
            );
        }

        member.setRole(request.role());
        return organizationMapper.toMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID targetUserId) {
        Organization organization = findMyOrganization();
        OrganizationMember member = findMembership(
                organization.getId(),
                targetUserId
        );

        if (member.getStatus() == MembershipStatus.REMOVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organization member has already been removed"
            );
        }

        member.markAsRemoved();
    }

    @Override
    @Transactional
    public MemberResponse acceptInvitation(String token) {
        UUID currentUserId = extractCurrentUserId(getCurrentJwt());
        OrganizationMember member = memberRepository
                .findByInvitationToken(token)
                .orElseThrow(() -> invalidInvitation(HttpStatus.NOT_FOUND));

        if (!member.getUser().getId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This organization invitation was not sent to you"
            );
        }
        if (!member.isInvitationPending()) {
            throw invalidInvitation(HttpStatus.CONFLICT);
        }
        if (isInvitationExpired(member)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Organization invitation has expired"
            );
        }

        member.accept();
        return organizationMapper.toMemberResponse(member);
    }

    @Override
    @Transactional
    public OrganizationResponse approve(UUID id) {
        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);
        Organization organization = findOrganizationForReview(id);
        if (!companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Company email must be verified before approval"
            );
        }
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setVerifiedAt(LocalDateTime.now());
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResponse reject(UUID id) {
        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);
        Organization organization = findOrganizationForReview(id);
        organization.setStatus(OrganizationStatus.REJECTED);
        organization.setVerifiedAt(null);
        return organizationMapper.toOrganizationResponse(organization);
    }

    private Organization findMyOrganization() {
        return findMyOrganization(extractCurrentUserId(getCurrentJwt()));
    }

    private Organization findMyOrganization(UUID ownerId) {
        return organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The authenticated company does not own an organization"
                ));
    }

    private Organization findOrganizationForReview(UUID id) {
        Organization organization = organizationRepository.findById(id)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> organizationNotFound());

        if (organization.getStatus() == OrganizationStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organization has already been approved"
            );
        }
        return organization;
    }

    private OrganizationMember findMembership(
            UUID organizationId,
            UUID userId
    ) {
        return memberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Organization membership has not been found"
                ));
    }

    private Jwt getCurrentJwt() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "A valid Keycloak access token is required"
            );
        }
        return jwtAuthentication.getToken();
    }

    private UUID extractCurrentUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private String requireEmail(Jwt jwt) {
        String email = trimToNull(jwt.getClaimAsString("email"));
        if (email == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Keycloak access token does not contain an email claim"
            );
        }
        return email.toLowerCase(Locale.ROOT);
    }

    private void requireRealmRole(Jwt jwt, String requiredRole) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            throw missingRole(requiredRole);
        }

        Object rolesClaim = realmAccess.get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)
                || roles.stream()
                .map(String::valueOf)
                .noneMatch(role -> role.equalsIgnoreCase(requiredRole))) {
            throw missingRole(requiredRole);
        }
    }

    private ResponseStatusException missingRole(String role) {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only " + role + " accounts can perform this action"
        );
    }

    private boolean isInvitationExpired(OrganizationMember member) {
        LocalDateTime invitationDate = member.getUpdatedAt() != null
                ? member.getUpdatedAt()
                : member.getCreatedAt();
        return invitationDate == null
                || invitationDate.isBefore(
                LocalDateTime.now().minusDays(INVITATION_VALID_DAYS)
        );
    }

    private ResponseStatusException invalidInvitation(HttpStatus status) {
        return new ResponseStatusException(
                status,
                "Organization invitation is invalid or no longer active"
        );
    }

    private ResponseStatusException organizationNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Organization has not been found"
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateUniqueSlug(String companyName) {
        String baseSlug = companyName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (baseSlug.isBlank()) {
            baseSlug = "company";
        }
        if (baseSlug.length() > 90) {
            baseSlug = baseSlug.substring(0, 90)
                    .replaceAll("-+$", "");
        }

        String slug = baseSlug;
        int suffix = 2;
        while (organizationRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + suffix++;
        }
        return slug;
    }

    private void registerIdentityRollbackCleanup(
            RegisteredCompany company,
            AtomicBoolean identityDeleted
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            deleteCompanyIdentity(company, identityDeleted);
                        }
                    }
                }
        );
    }

    private void deleteCompanyIdentity(
            RegisteredCompany company,
            AtomicBoolean identityDeleted
    ) {
        if (identityDeleted.compareAndSet(false, true)) {
            companyIdentityService.delete(company);
        }
    }
}
