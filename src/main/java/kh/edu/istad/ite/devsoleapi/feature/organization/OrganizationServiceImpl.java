package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InviteMemberRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.InvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.MemberResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationStatsResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationVerificationResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.RejectOrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberRoleRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.UpdateMemberPermissionsRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationNextAction;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationReviewDecision;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramMapper;
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
    private final WebsiteUrlService websiteUrlService;
    private final ImageStorageService imageStorageService;
    private final CompanyIdentityService companyIdentityService;
    private final OrganizationReviewHistoryRepository reviewHistoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProgramRepository programRepository;
    private final ProgramMapper programMapper;
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

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse me() {
        Organization organization = findMyOrganization();
        return organizationMapper.toOrganizationResponse(
                organization,
                statsFor(organization, true)
        );
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

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMyMembers() {
        Organization organization = findMyActiveOrganization();
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
        Organization organization = findMyActiveOrganization();
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
        if (request.permissions() == null) {
            member.applyRoleDefaults();
        } else {
            member.setPermissions(request.permissions());
        }
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
        Organization organization = findMyActiveOrganization();
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
        member.applyRoleDefaults();
        return organizationMapper.toMemberResponse(member);
    }

    @Override
    @Transactional
    public MemberResponse updateMemberPermissions(
            UUID targetUserId,
            UpdateMemberPermissionsRequest request
    ) {
        Organization organization = findMyActiveOrganization();
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

        member.setPermissions(request.permissions());
        return organizationMapper.toMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeMember(UUID targetUserId) {
        Organization organization = findMyActiveOrganization();
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
        requireActiveOrganization(member.getOrganization());

        member.accept();
        return organizationMapper.toMemberResponse(member);
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

    private Organization findMyActiveOrganization() {
        return requireActiveOrganization(findMyOrganization());
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


    @Override
    @Transactional(readOnly = true) // Crucial for loading lazy collections like assets
    public List<ProgramSummaryResponseDto> getOrganizationPrograms(UUID id) {
        //Fetch the programs by organization ID
        List<Program> programs = programRepository.findByOrganizationId(id);

        // DEBUG: Check your console/terminal. If this prints 0, the UUID in Postman is wrong!
        System.out.println("DEBUG -> Programs found in DB: " + programs.size());

        //Fetch the organization
        Organization organization = organizationRepository.findById(id).orElse(null);

        //Map to DTOs
        return programs.stream()
                .map(program -> programMapper.toSummaryDto(
                        program,
                        organization,
                        program.getAssets()
                ))
                .toList();
    }
}
