package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationMembershipResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.RejectOrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRoleResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.PendingInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberPermissionsRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationNextAction;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationReviewDecision;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRewardRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UsernameAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final long INVITATION_VALID_DAYS = 7;
    private static final long VERIFICATION_EMAIL_COOLDOWN_MINUTES = 1;

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationUserProfileRepository userProfileRepository;
    private final OrganizationMapper organizationMapper;
    private final OrganizationAuthorizationService organizationAuthorization;
    private final UsernameAllocator usernameAllocator;
    private final WebsiteUrlService websiteUrlService;
    private final ImageStorageService imageStorageService;
    private final CompanyIdentityService companyIdentityService;
    private final OrganizationReviewHistoryRepository reviewHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProgramRepository programRepository;
    private final ProgramService programService;
    private final ReportRepository reportRepository;
    private final ReportRewardRepository reportRewardRepository;

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

        String phone = normalizePhone(request.phone());
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
            // Company registration never asks for a handle, but a profile
            // saved without one is rejected at flush — so one is minted from
            // the business email the same way a social login's is.
            owner.setUsername(usernameAllocator.allocate(
                    null,
                    registeredCompany.email()
            ));
            owner.setEmail(registeredCompany.email());
            owner.setFullName(registeredCompany.fullName());
            owner.setPhone(phone);
            owner.setStatus(UserStatus.ACTIVE);
            owner = userProfileRepository.saveAndFlush(owner);

            Organization organization = organizationMapper.toOrganization(
                    request,
                    owner,
                    slug
            );
            organization.setStatus(OrganizationStatus.PENDING);
            organization.setSubmissionVersion(1);
            organization.setVerificationEmailSentAt(LocalDateTime.now());

            Organization saved = organizationRepository.saveAndFlush(
                    organization
            );
            publishLifecycleEvent(
                    saved,
                    OrganizationLifecycleEventType.REGISTERED,
                    null
            );
            return organizationMapper.toOrganizationResponse(saved);
        } catch (RuntimeException exception) {
            deleteCompanyIdentity(registeredCompany, identityDeleted);
            throw exception;
        }
    }

    /**
     * The organization behind the caller's company workspace.
     *
     * <p>Resolved the same way the rest of the workspace is — owned first,
     * then joined — rather than by ownership alone. A member who accepted an
     * invitation works at the company too, and answering them "you do not own
     * an organization" left every screen built on this endpoint blank for
     * everyone but whoever registered it.
     */
    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse me(UUID organizationId) {
        Organization organization = findMyTeamOrganization(
                extractCurrentUserId(getCurrentJwt()),
                organizationId
        );
        return organizationMapper.toOrganizationResponse(
                organization,
                statsFor(organization, true)
        );
    }

    @Override
    public List<OrganizationRoleResponse> getRoles() {
        return Arrays.stream(OrgRole.values())
                .map(role -> new OrganizationRoleResponse(
                        role,
                        OrganizationPermission.defaultsFor(role),
                        OrganizationPermission.ceilingFor(role)
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationVerificationResponse getVerificationStatus() {
        Organization organization = findMyOrganization();
        boolean emailVerified = companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        );
        return new OrganizationVerificationResponse(
                organization.getId(),
                organization.getStatus(),
                emailVerified,
                nextAction(organization.getStatus(), emailVerified),
                verificationEmailCanBeResentAt(organization)
        );
    }

    @Override
    @Transactional
    public void resendVerificationEmail() {
        Organization organization = findMyOrganization();
        UUID ownerId = organization.getOwner().getId();
        if (companyIdentityService.isEmailVerified(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Company email is already verified"
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime canResendAt = verificationEmailCanBeResentAt(
                organization
        );
        if (canResendAt != null && now.isBefore(canResendAt)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Verification email can be resent after " + canResendAt
            );
        }

        companyIdentityService.sendVerificationEmail(ownerId);
        organization.setVerificationEmailSentAt(now);
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
                if (organization.getStatus() == OrganizationStatus.ACTIVE) {
                    prepareForReview(organization);
                    organization.setSubmissionVersion(
                            organization.getSubmissionVersion() + 1
                    );
                    publishLifecycleEvent(
                            organization,
                            OrganizationLifecycleEventType.RESUBMITTED,
                            null
                    );
                }
            }
        }

        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResponse uploadLogo(MultipartFile file) {
        Organization organization = findMyOrganization();
        String logoUrl = imageStorageService.replace(
                "organizations/" + organization.getId(),
                organization.getLogoUrl(),
                file
        );
        organization.setLogoUrl(logoUrl);
        Organization saved = organizationRepository.saveAndFlush(
                organization
        );
        return organizationMapper.toOrganizationResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationResponse removeLogo() {
        Organization organization = findMyOrganization();
        imageStorageService.remove(organization.getLogoUrl());
        organization.setLogoUrl(null);
        Organization saved = organizationRepository.saveAndFlush(
                organization
        );
        return organizationMapper.toOrganizationResponse(saved);
    }

    /**
     * Under its own key prefix so a bucket listing tells covers and logos
     * apart. Sharing one would work — {@code replace} drops only the URL it is
     * handed — but leaves the two indistinguishable outside the database.
     */
    @Override
    @Transactional
    public OrganizationResponse uploadCoverImage(MultipartFile file) {
        Organization organization = findMyOrganization();
        String coverImageUrl = imageStorageService.replace(
                "organizations/" + organization.getId() + "/cover",
                organization.getCoverImageUrl(),
                file
        );
        organization.setCoverImageUrl(coverImageUrl);
        Organization saved = organizationRepository.saveAndFlush(
                organization
        );
        return organizationMapper.toOrganizationResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationResponse removeCoverImage() {
        Organization organization = findMyOrganization();
        imageStorageService.remove(organization.getCoverImageUrl());
        organization.setCoverImageUrl(null);
        Organization saved = organizationRepository.saveAndFlush(
                organization
        );
        return organizationMapper.toOrganizationResponse(saved);
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
        return organizationMapper.toOrganizationResponse(
                organization,
                statsFor(organization, false)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getBySlug(String slug) {
        Organization organization = organizationRepository
                .findBySlugAndStatusAndDeletedAtIsNull(slug, OrganizationStatus.ACTIVE)
                .orElseThrow(() -> organizationNotFound());
        return organizationMapper.toOrganizationResponse(
                organization,
                statsFor(organization, false)
        );
    }

    /**
     * The four headline numbers on an organization's profile.
     *
     * @param includeHiddenPrograms whether private and invite-only programs
     *                              count towards the program total. They do on
     *                              the organization's own profile and not on
     *                              the public one, where the number sits
     *                              beside a list that omits them and would
     *                              otherwise disagree with it. Payout and
     *                              report figures are deliberately not scoped
     *                              this way: they are what researchers judge
     *                              an organization by, and an aggregate names
     *                              no program.
     */
    private OrganizationStatsResponse statsFor(
            Organization organization,
            boolean includeHiddenPrograms
    ) {
        UUID organizationId = organization.getId();

        long activePrograms = includeHiddenPrograms
                ? programRepository
                .countByOrganizationIdAndStateAndDeletedAtIsNull(
                        organizationId,
                        ProgramState.ACTIVE
                )
                : programRepository
                .countByOrganizationIdAndStateAndVisibilityAndDeletedAtIsNull(
                        organizationId,
                        ProgramState.ACTIVE,
                        Visibility.PUBLIC
                );

        long resolvedReports = reportRepository.countByOrganizationAndState(
                organizationId,
                ReportState.RESOLVED
        );

        ReportRewardRepository.OrganizationPayouts payouts =
                reportRewardRepository.findOrganizationPayouts(organizationId);

        return new OrganizationStatsResponse(
                activePrograms,
                resolvedReports,
                payouts.getTotalDisbursed(),
                payouts.getTopAward()
        );
    }

    /**
     * The whole team, owner included, for anybody on it.
     *
     * <p>Readable by any active member rather than the owner alone: a triager
     * works alongside colleagues, and a roster they cannot open is a roster
     * they cannot hand a report to. Managing the team still needs
     * {@code MANAGE_MEMBERS} — seeing who is here and deciding who is here are
     * different questions.
     *
     * <p>The owner leads the list and is synthesised rather than read, since
     * ownership is not a membership row.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMyMembers(UUID organizationId) {
        UUID callerId = extractCurrentUserId(getCurrentJwt());
        Organization organization = findMyTeamOrganization(
                callerId,
                organizationId
        );

        List<MemberResponse> roster = new ArrayList<>();
        roster.add(organizationMapper.toOwnerMemberResponse(
                organization,
                callerId
        ));
        memberRepository
                .findByOrganizationIdAndStatusNot(
                        organization.getId(),
                        MembershipStatus.REMOVED
                )
                .stream()
                .map(member -> organizationMapper.toMemberResponse(
                        member,
                        callerId
                ))
                .forEach(roster::add);
        return List.copyOf(roster);
    }

    @Override
    @Transactional
    public InvitationResponse inviteMember(
            UUID organizationId,
            InviteMemberRequest request
    ) {
        Organization organization = findManageableOrganization(organizationId);
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
            if (member.isInvitationPending() && !member.isInvitationExpired()) {
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
        if (request.permissions() == null) {
            member.applyRoleDefaults();
        } else {
            member.setPermissions(withinRoleCeiling(
                    request.role(),
                    request.permissions()
            ));
        }
        member.setStatus(MembershipStatus.SUSPENDED);
        member.setInvitedBy(invitedBy);
        member.setInvitationEmail(invitedUser.getEmail());
        member.setInvitationToken(UUID.randomUUID().toString());
        member.setJoinedAt(null);

        OrganizationMember savedMember = memberRepository.saveAndFlush(member);
        LocalDateTime expiresAt = savedMember.invitationExpiresAt();

        // Two channels for the same invitation: the in-app notification below
        // reaches whoever opens DevSolve, the email reaches everyone else.
        // Both carry the token, and either one accepted makes the membership
        // real.
        eventPublisher.publishEvent(new OrganizationInvitationEmailEvent(
                organization.getId(),
                organization.getName(),
                invitedBy.getFullName(),
                savedMember.getInvitationEmail(),
                invitedUser.getFullName(),
                savedMember.getRole(),
                savedMember.getInvitationToken(),
                expiresAt
        ));

        // The invitation token goes back to the inviter to pass on out of
        // band, so without this the invited user has no signal at all that
        // anything happened. Keyed on the token, which is regenerated every
        // time an invitation is issued — so re-inviting after one expires
        // notifies again, while a retry of the same invitation does not.
        eventPublisher.publishEvent(NotificationEvent.to(
                invitedUser.getId(),
                "You have been invited to " + organization.getName(),
                invitedBy.getFullName() + " invited you to join "
                        + organization.getName() + " as "
                        + savedMember.getRole() + ".",
                NotificationType.INVITATION,
                organization.getId(),
                "organization-invitation:"
                        + savedMember.getInvitationToken()
        ));

        return new InvitationResponse(
                organizationMapper.toMemberResponse(
                        savedMember,
                        extractCurrentUserId(getCurrentJwt())
                ),
                savedMember.getInvitationToken(),
                expiresAt
        );
    }

    /**
     * A role change resets the permission set to that role's defaults rather
     * than leaving the old one in place. Keeping it would let a demotion be
     * cosmetic — a MANAGER moved to VIEWER who still held MANAGE_MEMBERS is
     * still a manager in every way that matters.
     */
    @Override
    @Transactional
    public MemberResponse updateMemberRole(
            UUID organizationId,
            UUID targetUserId,
            UpdateMemberRoleRequest request
    ) {
        Organization organization = findManageableOrganization(organizationId);
        OrganizationMember member = findManageableMembership(
                organization,
                targetUserId
        );

        member.setRole(request.role());
        member.applyRoleDefaults();
        return organizationMapper.toMemberResponse(
                member,
                extractCurrentUserId(getCurrentJwt())
        );
    }

    @Override
    @Transactional
    public MemberResponse updateMemberPermissions(
            UUID organizationId,
            UUID targetUserId,
            UpdateMemberPermissionsRequest request
    ) {
        Organization organization = findManageableOrganization(organizationId);
        OrganizationMember member = findManageableMembership(
                organization,
                targetUserId
        );

        member.setPermissions(withinRoleCeiling(
                member.getRole(),
                request.permissions()
        ));
        return organizationMapper.toMemberResponse(
                member,
                extractCurrentUserId(getCurrentJwt())
        );
    }

    @Override
    @Transactional
    public void removeMember(UUID organizationId, UUID targetUserId) {
        Organization organization = findManageableOrganization(organizationId);
        OrganizationMember member = findManageableMembership(
                organization,
                targetUserId
        );

        member.markAsRemoved();
    }

    /**
     * The permissions as asked for, once the role is known to allow them.
     *
     * <p>Role and permissions were independent, so a VIEWER could be granted
     * CREATE_PROGRAM and the API took it — leaving a client that gates on
     * permissions letting that person create programs under a badge reading
     * "Viewer". The refusal names the offending values so the client can point
     * at them instead of rejecting the whole form.
     */
    private Set<OrganizationPermission> withinRoleCeiling(
            OrgRole role,
            Set<OrganizationPermission> permissions
    ) {
        Set<OrganizationPermission> allowed =
                OrganizationPermission.ceilingFor(role);

        EnumSet<OrganizationPermission> denied =
                EnumSet.noneOf(OrganizationPermission.class);
        denied.addAll(permissions);
        denied.removeAll(allowed);

        if (!denied.isEmpty()) {
            throw new DetailedApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "The " + role + " role may not hold " + denied,
                    Map.of(
                            "role", role.name(),
                            "deniedPermissions", names(denied),
                            "allowedPermissions", names(allowed)
                    )
            );
        }
        return permissions;
    }

    private List<String> names(Collection<OrganizationPermission> permissions) {
        return permissions.stream()
                .map(OrganizationPermission::name)
                .toList();
    }

    /**
     * Every organization the caller can act in, owned or joined.
     *
     * <p>The one endpoint that answers "does this account belong to a company".
     * Nothing else could: {@link #me()} and every other {@code /me} route
     * resolves an organization by owner, so an accepted invitation was written
     * to {@code organization_members} and then never read back — the member's
     * role and permissions survived only in the response to the accept call
     * itself, and were gone by their next sign-in. The Keycloak token is no
     * help either, as the COMPANY realm role is granted at company
     * registration and accepting an invitation does not, and should not, hand
     * a researcher account a second platform role.
     *
     * <p>Owned first, then joined, oldest membership first. Organizations that
     * are not ACTIVE are kept rather than filtered: the caller does belong to
     * one under review, and each entry carries the status so the client can say
     * so instead of showing nothing.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMembershipResponse> getMyMemberships() {
        UUID currentUserId = extractCurrentUserId(getCurrentJwt());
        Map<UUID, OrganizationMembershipResponse> memberships =
                new LinkedHashMap<>();

        organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(currentUserId)
                .ifPresent(organization -> memberships.put(
                        organization.getId(),
                        organizationMapper.toOwnerMembership(organization)
                ));

        memberRepository
                .findByUserIdAndStatus(currentUserId, MembershipStatus.ACTIVE)
                .stream()
                .filter(member ->
                        member.getOrganization().getDeletedAt() == null
                )
                .sorted(Comparator.comparing(
                        OrganizationMember::getJoinedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .forEach(member -> memberships.putIfAbsent(
                        member.getOrganization().getId(),
                        organizationMapper.toMembership(member)
                ));

        return List.copyOf(memberships.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> getMyInvitations() {
        UUID currentUserId = extractCurrentUserId(getCurrentJwt());
        return memberRepository
                .findPendingInvitations(
                        currentUserId,
                        MembershipStatus.SUSPENDED
                )
                .stream()
                // Only what the caller could accept right now. An expired
                // invitation, or one into an organization that is no longer
                // active, is rejected by acceptInvitation — listing it would
                // make this endpoint another log of things that once
                // happened, which is the problem it exists to solve.
                .filter(member -> !member.isInvitationExpired())
                .filter(member -> member.getOrganization().getStatus()
                        == OrganizationStatus.ACTIVE)
                // Closest to expiring first: that is the one needing an answer.
                .sorted(Comparator.comparing(
                        OrganizationMember::invitationExpiresAt
                ))
                .map(organizationMapper::toPendingInvitation)
                .toList();
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
        if (member.isInvitationExpired()) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Organization invitation has expired"
            );
        }
        requireActiveOrganization(member.getOrganization());

        member.accept();
        return organizationMapper.toMemberResponse(member, currentUserId);
    }

    @Override
    @Transactional
    public OrganizationResponse approve(UUID id) {
        Jwt jwt = getCurrentJwt();
        requireRealmRole(jwt, ADMIN_ROLE);
        Organization organization = findPendingOrganizationForReview(id);
        if (!companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Company email must be verified before approval"
            );
        }
        LocalDateTime reviewedAt = LocalDateTime.now();
        UUID reviewerId = extractCurrentUserId(jwt);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setVerifiedAt(reviewedAt);
        organization.setReviewedBy(reviewerId);
        organization.setReviewedAt(reviewedAt);
        organization.setRejectionReason(null);
        recordReview(
                organization,
                OrganizationReviewDecision.APPROVED,
                reviewerId,
                null,
                reviewedAt
        );
        publishLifecycleEvent(
                organization,
                OrganizationLifecycleEventType.APPROVED,
                null
        );
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResponse reject(
            UUID id,
            RejectOrganizationRequest request
    ) {
        Jwt jwt = getCurrentJwt();
        requireRealmRole(jwt, ADMIN_ROLE);
        Organization organization = findPendingOrganizationForReview(id);
        LocalDateTime reviewedAt = LocalDateTime.now();
        UUID reviewerId = extractCurrentUserId(jwt);
        String reason = request == null
                ? null
                : trimToNull(request.reason());
        if (reason == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required"
            );
        }
        organization.setStatus(OrganizationStatus.REJECTED);
        organization.setVerifiedAt(null);
        organization.setReviewedBy(reviewerId);
        organization.setReviewedAt(reviewedAt);
        organization.setRejectionReason(reason);
        recordReview(
                organization,
                OrganizationReviewDecision.REJECTED,
                reviewerId,
                reason,
                reviewedAt
        );
        publishLifecycleEvent(
                organization,
                OrganizationLifecycleEventType.REJECTED,
                reason
        );
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResponse resubmit() {
        Jwt jwt = getCurrentJwt();
        Organization organization = findMyOrganization(
                extractCurrentUserId(jwt)
        );
        if (organization.getStatus() != OrganizationStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only rejected organization registrations can be resubmitted"
            );
        }
        if (!websiteUrlService.matchesEmailDomain(
                requireEmail(jwt),
                organization.getWebsiteUrl()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Business email domain must match the organization website domain"
            );
        }

        organization.setSubmissionVersion(
                organization.getSubmissionVersion() + 1
        );
        prepareForReview(organization);
        publishLifecycleEvent(
                organization,
                OrganizationLifecycleEventType.RESUBMITTED,
                null
        );
        return organizationMapper.toOrganizationResponse(organization);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationReviewSummaryResponse> getOrganizationsForAdmin(
            String query,
            OrganizationStatus status,
            int pageNumber,
            int pageSize
    ) {
        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);
        validatePagination(pageNumber, pageSize);

        String normalizedQuery = trimToNull(query);
        String queryPattern = normalizedQuery == null
                ? null
                : "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return organizationRepository.findForAdmin(
                queryPattern,
                status,
                pageable
        ).map(organizationMapper::toReviewSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationReviewSummaryResponse> getPendingOrganizations(
            int pageNumber,
            int pageSize
    ) {

        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);

        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        Sort.Direction.ASC,
                        "createdAt"
                )
        );

        return organizationRepository
                .findByStatusAndDeletedAtIsNull(
                        OrganizationStatus.PENDING,
                        pageable
                )
                .map(organizationMapper::toReviewSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationReviewResponse getForReview(UUID id) {
        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);
        Organization organization = organizationRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> organizationNotFound());
        boolean emailVerified = companyIdentityService.isEmailVerified(
                organization.getOwner().getId()
        );
        return organizationMapper.toReviewResponse(
                organization,
                emailVerified
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationReviewHistoryResponse> getReviewHistory(
            UUID id,
            int pageNumber,
            int pageSize
    ) {
        requireRealmRole(getCurrentJwt(), ADMIN_ROLE);
        validatePagination(pageNumber, pageSize);
        if (!organizationRepository.existsByIdAndDeletedAtIsNull(id)) {
            throw organizationNotFound();
        }
        return reviewHistoryRepository.findByOrganization_Id(
                id,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "reviewedAt")
                )
        ).map(organizationMapper::toReviewHistoryResponse);
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

    private Organization requireActiveOrganization(
            Organization organization
    ) {
        if (organization.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Organization must be approved before managing members"
            );
        }
        return organization;
    }

    private OrganizationNextAction nextAction(
            OrganizationStatus status,
            boolean emailVerified
    ) {
        if (status == OrganizationStatus.ACTIVE) {
            return OrganizationNextAction.NONE;
        }
        if (status == OrganizationStatus.REJECTED) {
            return OrganizationNextAction.CORRECT_AND_RESUBMIT;
        }
        return emailVerified
                ? OrganizationNextAction.WAIT_FOR_REVIEW
                : OrganizationNextAction.VERIFY_EMAIL;
    }

    /**
     * Punctuation out, one optional leading {@code +} kept. The profile column
     * accepts digits only, so the separators people type have to come off
     * before the row is written rather than at flush, where the failure is a
     * constraint violation naming a field the caller never sent.
     */
    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Phone number must contain between 8 and 15 digits"
            );
        }
        return phone.trim().startsWith("+") ? "+" + digits : digits;
    }

    private LocalDateTime verificationEmailCanBeResentAt(
            Organization organization
    ) {
        return organization.getVerificationEmailSentAt() == null
                ? null
                : organization.getVerificationEmailSentAt().plusMinutes(
                        VERIFICATION_EMAIL_COOLDOWN_MINUTES
                );
    }

    private void prepareForReview(Organization organization) {
        organization.setStatus(OrganizationStatus.PENDING);
        organization.setVerifiedAt(null);
        organization.setReviewedBy(null);
        organization.setReviewedAt(null);
        organization.setRejectionReason(null);
    }

    private void recordReview(
            Organization organization,
            OrganizationReviewDecision decision,
            UUID reviewerId,
            String reason,
            LocalDateTime reviewedAt
    ) {
        OrganizationReviewHistory history = new OrganizationReviewHistory();
        history.setOrganization(organization);
        history.setSubmissionVersion(organization.getSubmissionVersion());
        history.setDecision(decision);
        history.setReviewerId(reviewerId);
        history.setReason(reason);
        history.setReviewedAt(reviewedAt);
        reviewHistoryRepository.save(history);
    }

    private void publishLifecycleEvent(
            Organization organization,
            OrganizationLifecycleEventType type,
            String reason
    ) {
        eventPublisher.publishEvent(new OrganizationLifecycleEvent(
                type,
                organization.getId(),
                organization.getOwner().getId(),
                organization.getName(),
                organization.getSubmissionVersion(),
                reason
        ));
    }

    private void validatePagination(int pageNumber, int pageSize) {
        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0"
            );
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100"
            );
        }
    }

    private Organization findPendingOrganizationForReview(UUID id) {
        Organization organization = organizationRepository
                .findByIdForReview(id)
                .orElseThrow(() -> organizationNotFound());

        if (organization.getStatus() != OrganizationStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending organization registrations can be reviewed"
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

    /**
     * The organization whose team the caller may look at.
     *
     * <p>An owner sees their own team at any status: a company still under
     * review has a team screen too, and its own roster is not something the
     * review withholds from them. Everyone else is resolved through
     * membership, which only an active organization can grant.
     *
     * @param organizationId which organization, for an account that belongs to
     *                       more than one. Null asks for the only one there is.
     */
    private Organization findMyTeamOrganization(
            UUID callerId,
            UUID organizationId
    ) {
        return organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(callerId)
                .filter(owned -> organizationId == null
                        || organizationId.equals(owned.getId()))
                .orElseGet(() -> organizationAuthorization
                        .findAccessibleOrganization(callerId, organizationId));
    }

    /**
     * The organization whose team the caller may change. Ownership still
     * carries every permission, so an owner reaches this without a membership
     * row; a manager reaches it through {@code MANAGE_MEMBERS}, which is what
     * lets a company grow its team without going back to whoever registered it.
     *
     * <p>An owner is checked for approval first so that a company still under
     * review is told that is the reason. Resolving by permission alone would
     * answer "you do not have MANAGE_MEMBERS" to the one person who does, and
     * a client cannot act on that.
     */
    private Organization findManageableOrganization(UUID organizationId) {
        UUID callerId = extractCurrentUserId(getCurrentJwt());
        organizationRepository
                .findByOwnerIdAndDeletedAtIsNull(callerId)
                .filter(owned -> organizationId == null
                        || organizationId.equals(owned.getId()))
                .ifPresent(this::requireActiveOrganization);

        return organizationAuthorization.findAccessibleOrganization(
                callerId,
                organizationId,
                OrganizationPermission.MANAGE_MEMBERS
        );
    }

    /**
     * A membership the caller is allowed to act on.
     *
     * <p>Two targets are refused outright rather than left to fail further in.
     * The owner has no membership row at all, so acting on them used to answer
     * "not found" — true of the row and misleading about the person, now that
     * the roster shows them. And nobody may act on themselves: a manager who
     * can demote or remove themselves can lock a company out of its own team
     * screen, and the client should not be offering it either.
     */
    private OrganizationMember findManageableMembership(
            Organization organization,
            UUID targetUserId
    ) {
        if (organization.getOwner().getId().equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The organization owner's membership cannot be changed"
            );
        }
        if (extractCurrentUserId(getCurrentJwt()).equals(targetUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot change your own organization membership"
            );
        }

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
        return member;
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


    @Override
    @Transactional(readOnly = true)
    public List<ProgramSummaryResponseDto> getOrganizationPrograms(UUID id) {
        return programService.getPublicPrograms(
                id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Pageable.unpaged()
        ).getContent();
    }
}
