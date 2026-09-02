package kh.edu.istad.ite.devsoleapi.feature.recognition;


import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.Hacktivity;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityEventType;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMember;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.MembershipStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ThanksResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reputation.ReputationPolicy;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecognitionServiceImpl implements RecognitionService {

    private static final String PLATFORM_ADMIN_ROLE = "ADMIN";

    private final RecognitionRepository recognitionRepository;

    private final RecognitionMapper recognitionMapper;

    private final HacktivityRepository hacktivityRepository;

    private final UserProfileRepository userProfileRepository;

    private final ReportRepository reportRepository;

    private final ProgramRepository programRepository;

    private final OrganizationRepository organizationRepository;

    private final OrganizationMemberRepository organizationMemberRepository;

    private final ApplicationEventPublisher eventPublisher;


    /**
     * Awards a recognition: the organization's public credit for a finding it
     * has already fixed.
     *
     * <p>It does not move reputation. The researcher was paid for this finding
     * when the report was resolved, priced by severity, and nothing on this
     * platform subtracts reputation — so paying again here would be a second,
     * irreversible award for one bug. What a recognition adds is the credit
     * itself: a titled entry on the researcher's profile, a row on the public
     * feed, and one more on their recognition count.
     *
     * <p>The recognition row, the hacktivity entry and the counter land in one
     * transaction or not at all. Splitting them would let a crash leave a
     * recognition that never reached the feed, or a count nobody can trace back
     * to a finding.
     *
     * <p>The leaderboard is dropped wholesale rather than by key. The board
     * prints recognition and critical counts, and a count is read alongside a
     * rank — a rank being a position among everybody else, so one row changing
     * rewrites every page below it. Evicting only the page the researcher sits
     * on would leave the rest of the board disagreeing with it.
     */
    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.LEADERBOARD, allEntries = true)
    public RecognitionResponse awardRecognition(
            CreateRecognitionRequest request,
            UUID awardedBy
    ) {

        UserProfile user = userProfileRepository
                .findById(request.userId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User profile not found: " + request.userId()
                        )
                );

        Report report = reportRepository
                .findById(request.reportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Report not found: " + request.reportId()
                        )
                );

        Program program = programRepository
                .findById(request.programId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Program not found: " + request.programId()
                        )
                );

        // The report already knows which program was tested and who tested it.
        // Those two facts are checked against the request rather than taken
        // from it: trusting the body let a triager pin somebody else's finding
        // to any program, and attribute it to any user, on a feed that is
        // public.
        if (!report.getProgram().getId().equals(program.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Report " + report.getId()
                            + " was not submitted to program " + program.getId()
            );
        }

        if (!report.getReporter().getId().equals(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recognition can only be awarded to the researcher who "
                            + "reported the finding"
            );
        }

        if (report.getState() != ReportState.RESOLVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recognition can only be awarded for a resolved report"
            );
        }

        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Organization not found: "
                                        + program.getOrganizationId()
                        )
                );

        requireActiveMemberOf(organization.getId(), awardedBy);

        // severity is settled by a database trigger and stays NULL while the
        // reported and triage severities disagree or a dispute is open (see
        // reconcile_report_severity in schema.sql). Awarding then would price
        // the finding off a severity nobody has agreed to, so refuse until it
        // is resolved.
        Severity severity = report.getSeverity();
        if (severity == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Report severity is still unsettled; resolve the severity "
                            + "dispute before awarding recognition"
            );
        }

        if (recognitionRepository.existsByReportId(report.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Report " + report.getId()
                            + " has already been recognised"
            );
        }

        Recognition recognition = new Recognition();

        recognition.setUserId(request.userId());
        recognition.setProgramId(request.programId());
        recognition.setReportId(request.reportId());
        recognition.setTitle(request.title());
        recognition.setDescription(request.description());
        recognition.setAwardedBy(awardedBy);
        recognition.setAwardedAt(LocalDateTime.now());
        recognition.setSeverity(severity);

        try {
            // Flushed here rather than at commit so that the unique constraint
            // on report_id is enforced while this catch block is still on the
            // stack. Deferred to commit it would surface as an unhandled 500.
            recognition = recognitionRepository.saveAndFlush(recognition);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Report " + report.getId()
                            + " has already been recognised"
            );
        }

        hacktivityRepository.save(
                Hacktivity.builder()
                        .recognition(recognition)
                        .user(user)
                        .organization(organization)
                        .report(report)
                        .program(program)
                        .eventType(eventTypeFor(report))
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        countRecognition(user.getId());

        // No points quoted. The message used to promise reputation this award
        // no longer hands out — the researcher was paid when the report was
        // resolved — and a notice that names a number twice is worse than one
        // that names it once.
        eventPublisher.publishEvent(NotificationEvent.to(
                user.getId(),
                "You have been recognised",
                organization.getName() + " recognised your work on \""
                        + report.getTitle() + "\": " + recognition.getTitle()
                        + ".",
                NotificationType.RECOGNITION,
                recognition.getId(),
                "recognition:" + recognition.getId()
        ));

        return recognitionMapper.toResponse(recognition);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<RecognitionResponse> getRecognitionsByUser(
            UUID userId,
            Pageable pageable
    ) {

        if (!userProfileRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }

        return recognitionRepository
                .findAllByUserId(userId, pageable)
                .map(recognitionMapper::toResponse);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ThanksResponse> getProgramThanks(
            UUID programId,
            Pageable pageable
    ) {

        // Checked rather than left to return an empty board: a program that
        // does not exist and one that has thanked nobody are different
        // answers, and a page that renders "no thanks yet" for a mistyped id
        // is the same lie the profile feed tab was telling.
        if (!programRepository.existsById(programId)) {
            throw new ResourceNotFoundException(
                    "Program not found: " + programId
            );
        }

        return rank(
                recognitionRepository.tallyThanksByProgram(programId),
                pageable
        );
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ThanksResponse> getOrganizationThanks(
            UUID organizationId,
            Pageable pageable
    ) {

        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException(
                    "Organization not found: " + organizationId
            );
        }

        return rank(
                recognitionRepository
                        .tallyThanksByOrganization(organizationId),
                pageable
        );
    }


    /**
     * Folds the severity tallies into one row per researcher, ranks them, and
     * pages the result.
     *
     * <p>Ranked and paged in memory rather than by the database. The tie-break
     * needs the severity curve, which only {@link ReputationPolicy} knows how
     * to apply and which is not a column — expressing it as a CASE in JPQL
     * would be a second copy that drifts the day the curve is retuned. What is
     * read instead is one grouped row per researcher and severity, bounded by
     * how many people have been thanked here rather than by how many times.
     */
    private Page<ThanksResponse> rank(
            List<RecognitionRepository.ThanksTally> tallies,
            Pageable pageable
    ) {

        Map<UUID, ThanksStanding> standings = new LinkedHashMap<>();

        tallies.forEach(tally -> standings
                .computeIfAbsent(
                        tally.getUserId(),
                        id -> new ThanksStanding()
                )
                .add(
                        tally.getSeverity(),
                        tally.getThanks(),
                        tally.getLastAwardedAt()
                ));

        if (standings.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Suspended and removed accounts do not appear, the same rule the
        // leaderboard applies — and applied before paging, so their absence
        // does not leave holes in the ranks.
        Map<UUID, UserProfile> profiles = userProfileRepository
                .findAllById(standings.keySet())
                .stream()
                .filter(profile -> profile.getStatus() == UserStatus.ACTIVE)
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        Function.identity()
                ));

        List<UUID> ranked = standings.entrySet().stream()
                .filter(entry -> profiles.containsKey(entry.getKey()))
                .sorted(mostThankedFirst())
                .map(Map.Entry::getKey)
                .toList();

        int from = Math.min((int) pageable.getOffset(), ranked.size());
        int to = Math.min(from + pageable.getPageSize(), ranked.size());

        AtomicInteger rank = new AtomicInteger(from + 1);

        List<ThanksResponse> content = ranked.subList(from, to).stream()
                .map(id -> toThanksResponse(
                        profiles.get(id),
                        rank.getAndIncrement(),
                        standings.get(id)
                ))
                .toList();

        return new PageImpl<>(content, pageable, ranked.size());
    }


    /**
     * Most thanked first. A tie goes to whoever was thanked for the harder
     * findings, then to whoever was thanked most recently, and last to the id
     * — equal rows in an undefined order page unstably, duplicating some
     * researchers and skipping others.
     *
     * <p>Volume leads here, unlike the leaderboard, because nobody can farm
     * it: a researcher cannot thank themselves, and an organization handing
     * out forty thanks has decided forty times that it meant to.
     */
    private static Comparator<Map.Entry<UUID, ThanksStanding>>
            mostThankedFirst() {

        Comparator<Map.Entry<UUID, ThanksStanding>> byCount =
                Comparator.comparingLong(entry ->
                        entry.getValue().recognitions());

        Comparator<Map.Entry<UUID, ThanksStanding>> byDepth =
                Comparator.comparingInt(entry ->
                        entry.getValue().severityWeight());

        Comparator<Map.Entry<UUID, ThanksStanding>> byRecency =
                Comparator.comparing(
                        entry -> entry.getValue().lastThankedAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder())
                );

        return byCount.reversed()
                .thenComparing(byDepth.reversed())
                .thenComparing(byRecency.reversed())
                .thenComparing(Map.Entry::getKey);
    }


    private ThanksResponse toThanksResponse(
            UserProfile user,
            int rank,
            ThanksStanding standing
    ) {

        return new ThanksResponse(
                rank,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getCountry(),
                standing.recognitions(),
                standing.bySeverity(),
                standing.lastThankedAt()
        );
    }


    /**
     * What the feed row says happened.
     *
     * <p>Recorded once, here, rather than left for a reader to infer from
     * which nested object came back non-null. A finding paid in money reads
     * differently from one recognised alone, and only the write path knows
     * which this was without going back to the database.
     *
     * <p>A points-only reward is not a bounty: it moves the leaderboard, not
     * anybody's bank, so it stays a plain recognition.
     */
    private HacktivityEventType eventTypeFor(Report report) {

        // Null-guarded: Hibernate always hands back a collection, but a Report
        // built through the no-args constructor has never touched the field.
        List<ReportReward> rewards = report.getRewards();

        boolean paid = rewards != null && rewards.stream().anyMatch(reward ->
                reward.getAmount() != null
                        && reward.getAmount().signum() > 0
        );

        return paid
                ? HacktivityEventType.BOUNTY_AWARDED
                : HacktivityEventType.RECOGNITION_AWARDED;
    }


    /**
     * Only somebody who currently works for the organization behind the
     * program may recognise a finding against it. The controller's role check
     * proves the caller is <em>a</em> member somewhere, not a member here —
     * without this any member of any organization could award recognitions on
     * every program on the platform. Platform admins are exempt so support can
     * correct awards.
     */
    private void requireActiveMemberOf(UUID organizationId, UUID userId) {

        if (AuthUtils.hasRole(PLATFORM_ADMIN_ROLE)) {
            return;
        }

        OrganizationMember membership = organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Only members of the organization running this program "
                                + "can award recognition for it"
                ));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your membership of this organization is "
                            + membership.getStatus()
                            + " and cannot award recognition"
            );
        }
    }


    /**
     * Counts the recognition on the researcher's profile.
     *
     * <p>Reputation is untouched: it was priced by severity and paid when the
     * report was resolved. This is the count of times an organization has
     * credited them by name, which is a different thing to say about a
     * researcher and is why the leaderboard prints both.
     */
    private void countRecognition(UUID userId) {

        int updated = userProfileRepository.incrementRecognitionCount(userId);

        if (updated != 1) {
            // The profile was read at the top of this method, so losing it now
            // means it was deleted mid-award. Rolling back is the only honest
            // answer: the alternative is a recognition credited to nobody.
            throw new ResourceNotFoundException(
                    "User profile not found: " + userId
            );
        }

        log.info("Recorded a recognition for {}", userId);
    }
}
