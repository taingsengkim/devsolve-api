package kh.edu.istad.ite.devsoleapi.feature.recognition;


import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.Hacktivity;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityEventType;
import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationEvent;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ProgramSummary;
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
import java.util.Objects;
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

    private final OrganizationAuthorizationService organizationAuthorization;

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

        Report report = reportRepository
                .findById(request.reportId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Report not found: " + request.reportId()
                        )
                );

        // The report is the whole subject of the award, and it already knows
        // which program was tested and who tested it. Both are read off it
        // rather than out of the request body — deriving them is what makes it
        // impossible for a triager to pin somebody else's finding to another
        // program, or attribute it to another user, on a feed that is public.
        //
        // They were once looked up from the body and rejected on a mismatch,
        // which enforced the same rule but made the client restate two facts
        // it had just been served. A client that restated the program wrongly
        // got "Program not found" for a program sitting on the report in front
        // of it, and no correct request was possible until the client was
        // fixed. What the body says now is a hint, and a wrong hint is a
        // logged warning rather than a wall.
        Program program = report.getProgram();
        UserProfile user = report.getReporter();

        warnIfContradicted(
                "program", request.programId(), program.getId(), report
        );
        warnIfContradicted(
                "researcher", request.userId(), user.getId(), report
        );

        if (report.getState() != ReportState.RESOLVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recognition can only be awarded for a resolved report"
            );
        }

        Organization organization =
                requireCanAwardFor(program.getOrganizationId(), awardedBy);

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

        recognition.setUserId(user.getId());
        recognition.setProgramId(program.getId());
        recognition.setReportId(report.getId());
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

        return recognitionMapper.toResponse(
                recognition,
                summarise(program, organization)
        );
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

        Page<Recognition> page = recognitionRepository
                .findAllByUserId(userId, pageable);

        // Resolved once for the page rather than per row. A researcher's
        // recognitions cluster on a handful of programs, so this is two
        // queries whichever way the page falls, against one per row for a list
        // whose whole point is to name who thanked them.
        Map<UUID, ProgramSummary> programs = summarise(
                page.getContent().stream()
                        .map(Recognition::getProgramId)
                        .toList()
        );

        return page.map(recognition -> recognitionMapper.toResponse(
                recognition,
                programs.get(recognition.getProgramId())
        ));
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
                        tally.getProgramId(),
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

        List<UUID> page = ranked.subList(from, to);

        // Only the programs this page names, and only once each — the board
        // spans every program an organization runs, and the rows below the
        // fold are not worth a lookup.
        Map<UUID, ProgramSummary> programs = summarise(
                page.stream()
                        .map(id -> standings.get(id).programIds())
                        .flatMap(List::stream)
                        .toList()
        );

        List<ThanksResponse> content = page.stream()
                .map(id -> toThanksResponse(
                        profiles.get(id),
                        rank.getAndIncrement(),
                        standings.get(id),
                        programs
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
            ThanksStanding standing,
            Map<UUID, ProgramSummary> programs
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
                standing.programIds().stream()
                        .map(programs::get)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(
                                ProgramSummary::name,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .toList(),
                standing.lastThankedAt()
        );
    }


    /**
     * Names the programs behind a set of ids, in as few queries as there are
     * kinds of thing to name.
     *
     * <p>A program whose id resolves to nothing is dropped rather than
     * rendered as a blank card. Programs are soft-deleted, so this is the
     * erased-outright case, and a row that has lost the program it was
     * credited on is still a real thank-you — the count stands, the card just
     * cannot say where.
     */
    private Map<UUID, ProgramSummary> summarise(List<UUID> programIds) {

        List<UUID> wanted = programIds.stream().distinct().toList();

        if (wanted.isEmpty()) {
            return Map.of();
        }

        List<Program> programs = programRepository.findAllById(wanted);

        Map<UUID, Organization> organizations = organizationRepository
                .findAllById(programs.stream()
                        .map(Program::getOrganizationId)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        Organization::getId,
                        Function.identity()
                ));

        return programs.stream()
                .filter(program -> organizations
                        .containsKey(program.getOrganizationId()))
                .collect(Collectors.toMap(
                        Program::getId,
                        program -> summarise(
                                program,
                                organizations.get(program.getOrganizationId())
                        )
                ));
    }


    private ProgramSummary summarise(
            Program program,
            Organization organization
    ) {

        return new ProgramSummary(
                program.getId(),
                program.getName(),
                program.getHandle(),
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getLogoUrl()
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
     * Says so when the request disagrees with the report about who or what it
     * is crediting.
     *
     * <p>Logged rather than thrown. The award is settled from the report, so a
     * stale id in the body cannot credit the wrong person or the wrong program
     * — there is nothing here to refuse. It is still worth a line: a client
     * that keeps sending the wrong program is a client with a bug, and this is
     * the only place that can see it.
     */
    private void warnIfContradicted(
            String subject,
            UUID supplied,
            UUID actual,
            Report report
    ) {

        if (supplied != null && !supplied.equals(actual)) {
            log.warn(
                    "Recognition request named {} {} for report {}, whose {} "
                            + "is {}; awarding against the report",
                    subject, supplied, report.getId(), subject, actual
            );
        }
    }


    /**
     * Only somebody who can award for the organization behind the program may
     * recognise a finding against it. The controller's role check proves the
     * caller is staff <em>somewhere</em>, not staff here — without this any
     * member of any organization could award recognitions on every program on
     * the platform.
     *
     * <p>Delegated to {@link OrganizationAuthorizationService} rather than
     * asking {@code organization_members} directly, which is what this did and
     * why an organization's own owner was answered
     *
     * <pre>Only members of the organization running this program can award
     * recognition for it</pre>
     *
     * on their own program. Ownership is not modelled as a membership row, so
     * a lookup over members alone skips the one person who always has every
     * permission — the trap that service already documents and handles. It
     * also settles two things the local check never did: an inactive
     * organization cannot hand out credit, and a VIEWER, who may read reports
     * but award nothing, no longer can either.
     *
     * <p>{@link OrganizationPermission#AWARD_REWARDS} is the permission asked
     * for. Recognition is the credit half of what that permission covers, and
     * splitting it from bounties would mean a team could pay for a finding it
     * is not allowed to thank anybody for.
     *
     * <p>Platform admins stay exempt so support can correct awards.
     */
    private Organization requireCanAwardFor(UUID organizationId, UUID userId) {

        if (AuthUtils.hasRole(PLATFORM_ADMIN_ROLE)) {
            return organizationRepository
                    .findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Organization not found: " + organizationId
                    ));
        }

        return organizationAuthorization.requirePermission(
                organizationId,
                userId,
                OrganizationPermission.AWARD_REWARDS
        );
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
