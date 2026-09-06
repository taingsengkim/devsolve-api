package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.transaction.annotation.Transactional;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagTally;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagGroupResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagQueueSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.TargetFlagActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.takedown.ContentTakedownService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Service
@RequiredArgsConstructor

public class ContentFlagServiceImpl implements ContentFlagService{
    private final UserProfileRepository userProfileRepository;
    private final ContentFlagRepository contentFlagRepository;
    private final FlagQueueRepository flagQueueRepository;
    private final ContentFlagMapper contentFlagMapper;
    private final FlagRowAssembler flagRowAssembler;
    private final ContentTakedownService contentTakedownService;

    @Override
    @Transactional
    public FlagResponse createFlag(
            CreateFlagRequest request
    ) {

        UUID userId = extractCurrentUserId();

        UserProfile reporter = userProfileRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User profile has not been found"
                ));

        boolean alreadyReported =
                contentFlagRepository
                        .existsByReporter_IdAndFlaggableTypeAndFlaggableId(
                                userId,
                                request.flaggableType(),
                                request.flaggableId()
                        );

        if (alreadyReported) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reported this content"
            );
        }

        ContentFlag flag =
                contentFlagMapper
                        .mapCreateFlagRequestToContentFlag(request);

        flag.setReporter(reporter);
        flag.setSource(FlagSource.USER);
        flag.setStatus(FlagStatus.PENDING);

        ContentFlag savedFlag =
                contentFlagRepository.saveAndFlush(flag);

        return readFlag(savedFlag.getId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlagResponse> getMyFlags(
            FlagStatus status,
            int pageNumber,
            int pageSize
    ) {
        UUID reporterId = extractCurrentUserId();
        validatePagination(pageNumber, pageSize);

        // Reported through the same query the moderation queue uses, so a
        // reporter's own list shows what they reported rather than an ID. What
        // it does not show is how many other people reported the same thing —
        // see FlagResponse.
        return flagQueueRepository
                .search(
                        name(status),
                        null,
                        null,
                        reporterId.toString(),
                        null,
                        FlagQueueSort.NEWEST.name(),
                        page(pageNumber, pageSize)
                )
                .map(row -> flagRowAssembler.toResponse(row, false));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlagResponse> getAdminFlags(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            String search,
            FlagQueueSort sort,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin("Only ADMIN can view flags");

        validatePagination(pageNumber, pageSize);

        return flagQueueRepository
                .search(
                        name(status),
                        name(flaggableType),
                        name(reason),
                        null,
                        searchPattern(search),
                        sortOrDefault(sort).name(),
                        page(pageNumber, pageSize)
                )
                .map(row -> flagRowAssembler.toResponse(row, true));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlagGroupResponse> getAdminFlagGroups(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            String search,
            FlagQueueSort sort,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin("Only ADMIN can view flags");

        validatePagination(pageNumber, pageSize);

        return flagQueueRepository
                .searchGroups(
                        name(status),
                        name(flaggableType),
                        name(reason),
                        null,
                        searchPattern(search),
                        sortOrDefault(sort).name(),
                        page(pageNumber, pageSize)
                )
                .map(flagRowAssembler::toGroup);
    }

    /**
     * Every badge above the queue, from one pass over the flag table.
     *
     * <p>The breakdowns describe the open queue only; the three totals are of
     * everything. See {@link FlagQueueSummaryResponse}.
     */
    @Override
    @Transactional(readOnly = true)
    public FlagQueueSummaryResponse getQueueSummary() {
        requireAdmin("Only ADMIN can view flags");

        Map<FlagReason, Long> byReason = new EnumMap<>(FlagReason.class);
        Map<FlaggableType, Long> byType = new EnumMap<>(FlaggableType.class);
        for (FlagReason reason : FlagReason.values()) {
            byReason.put(reason, 0L);
        }
        for (FlaggableType type : FlaggableType.values()) {
            byType.put(type, 0L);
        }

        long pending = 0;
        long reviewed = 0;
        long dismissed = 0;

        for (FlagTally tally : flagQueueRepository.tally()) {
            FlagStatus status = parseStatus(tally.getStatus());
            if (status == null) {
                // A status retired from the enum but still on old rows. It is
                // not one of the three tabs, so it belongs in none of them.
                continue;
            }
            switch (status) {
                case PENDING -> {
                    pending += tally.getTotal();
                    countInto(byReason, tally.getReason(), tally.getTotal());
                    countInto(
                            byType,
                            tally.getFlaggableType(),
                            tally.getTotal()
                    );
                }
                case REVIEWED -> reviewed += tally.getTotal();
                case DISMISSED -> dismissed += tally.getTotal();
            }
        }

        return new FlagQueueSummaryResponse(
                pending,
                reviewed,
                dismissed,
                Map.copyOf(byReason),
                Map.copyOf(byType)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FlagResponse getAdminFlagById(UUID id) {
        requireAdmin("Only ADMIN can view flags");

        return readFlag(id, true);
    }

    @Override
    @Transactional
    public FlagResponse dismissFlag(
            UUID id
    ) {

        requireAdmin("Only ADMIN can dismiss flags");

        UUID adminId = extractCurrentUserId();

        ContentFlag flag = findPendingFlag(id);
        UserProfile admin = findAdmin(adminId);

        flag.setStatus(FlagStatus.DISMISSED);
        flag.setReviewedBy(admin);
        flag.setReviewedAt(LocalDateTime.now());

        contentFlagRepository.saveAndFlush(flag);

        return readFlag(id, true);
    }

    @Override
    @Transactional
    public FlagResponse resolveFlag(
            UUID id,
            ResolveFlagRequest request
    ) {
        requireAdmin("Only ADMIN can resolve flags");

        UUID adminId = extractCurrentUserId();
        ContentFlag flag = findPendingFlag(id);
        UserProfile admin = findAdmin(adminId);

        flag.setStatus(FlagStatus.REVIEWED);
        flag.setReviewedBy(admin);
        flag.setReviewedAt(LocalDateTime.now());
        flag.setResolutionNote(
                request.resolutionNote().trim()
        );

        if (request.removeContent()) {
            contentTakedownService.takeDown(
                    moderationTargetOf(flag.getFlaggableType()),
                    flag.getFlaggableId(),
                    flag.getResolutionNote()
            );
        }

        contentFlagRepository.saveAndFlush(flag);

        // Re-read rather than map the entity, so the response carries the same
        // resolved content the queue does — including, when the content has
        // just been taken down, that it is gone.
        return readFlag(id, true);
    }

    @Override
    @Transactional
    public TargetFlagActionResponse resolveTargetFlags(
            FlaggableType flaggableType,
            UUID flaggableId,
            ResolveFlagRequest request
    ) {
        requireAdmin("Only ADMIN can resolve flags");

        UserProfile admin = findAdmin(extractCurrentUserId());
        String note = request.resolutionNote().trim();

        List<ContentFlag> pending = closeAll(
                flaggableType,
                flaggableId,
                FlagStatus.REVIEWED,
                admin,
                note
        );

        // Outside the loop, and unconditional: the content comes down once
        // however many people reported it, and it comes down even when a
        // colleague has already closed every report — an admin who ticked
        // remove asked about the content, not about the paperwork.
        if (request.removeContent()) {
            contentTakedownService.takeDown(
                    moderationTargetOf(flaggableType),
                    flaggableId,
                    note
            );
        }

        return new TargetFlagActionResponse(
                flaggableType,
                flaggableId,
                pending.size(),
                request.removeContent()
        );
    }

    @Override
    @Transactional
    public TargetFlagActionResponse dismissTargetFlags(
            FlaggableType flaggableType,
            UUID flaggableId
    ) {
        requireAdmin("Only ADMIN can dismiss flags");

        UserProfile admin = findAdmin(extractCurrentUserId());

        List<ContentFlag> pending = closeAll(
                flaggableType,
                flaggableId,
                FlagStatus.DISMISSED,
                admin,
                null
        );

        return new TargetFlagActionResponse(
                flaggableType,
                flaggableId,
                pending.size(),
                false
        );
    }

    /**
     * Closes every open report on one piece of content.
     *
     * <p>An empty list is not an error. It means a colleague reached the same
     * card first, which two people working one queue will do, and failing the
     * request would only ask this admin to go and find out that nothing was
     * wrong.
     */
    private List<ContentFlag> closeAll(
            FlaggableType flaggableType,
            UUID flaggableId,
            FlagStatus decision,
            UserProfile admin,
            String note
    ) {
        List<ContentFlag> pending = contentFlagRepository
                .findByFlaggableTypeAndFlaggableIdAndStatus(
                        flaggableType,
                        flaggableId,
                        FlagStatus.PENDING
                );

        LocalDateTime reviewedAt = LocalDateTime.now();
        for (ContentFlag flag : pending) {
            flag.setStatus(decision);
            flag.setReviewedBy(admin);
            flag.setReviewedAt(reviewedAt);
            if (note != null) {
                flag.setResolutionNote(note);
            }
        }
        contentFlagRepository.saveAll(pending);
        return pending;
    }

    /**
     * The same content, named the way the moderation history names it.
     *
     * <p>Two enums for one idea, which is worth being explicit about: a flag
     * says what a reader can report, and a moderation action says what an
     * administrator can act on. They happen to agree on all five flaggable
     * kinds, and the mapping is spelled out rather than done by
     * {@code valueOf(name())} so that adding a value to one of them is a
     * compile error here instead of a runtime one on a moderator's screen.
     *
     * <p>All five types are now removable through here. They used to reach two
     * — comment and program — and throw 400 for the rest, which rolled the
     * whole transaction back and left the flag pending with the admin's
     * resolution note discarded. Every type going through
     * {@code ContentTakedownService} means resolving a flag and taking content
     * down from the admin console are the same act, recorded the same way.
     */
    private ModerationTargetType moderationTargetOf(FlaggableType type) {
        return switch (type) {
            case PROBLEM -> ModerationTargetType.PROBLEM;
            case SHOWCASE -> ModerationTargetType.SHOWCASE;
            case SOLUTION -> ModerationTargetType.SOLUTION;
            case COMMENT -> ModerationTargetType.COMMENT;
            case PROGRAM -> ModerationTargetType.PROGRAM;
        };
    }

    private FlagResponse readFlag(UUID id, boolean withSiblingCounts) {
        FlagRow row = flagQueueRepository
                .findRow(id.toString())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Flag not found"
                ));
        return flagRowAssembler.toResponse(row, withSiblingCounts);
    }

    private ContentFlag findFlag(UUID id) {
        return contentFlagRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Flag not found"
                ));
    }

    private ContentFlag findPendingFlag(UUID id) {
        ContentFlag flag = findFlag(id);

        if (flag.getStatus() != FlagStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Flag has already been reviewed"
            );
        }
        return flag;
    }

    private UserProfile findAdmin(UUID adminId) {
        return userProfileRepository
                .findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found"
                ));
    }

    private void requireAdmin(String message) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    message
            );
        }
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(
                    AuthUtils.extractUserId()
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    /**
     * Unsorted on purpose. The ordering is a parameter of the statement rather
     * than something Spring appends, because two of the three orderings are
     * over columns that only exist inside a lateral join — see
     * {@link FlagQueueQueries}.
     */
    private Pageable page(int pageNumber, int pageSize) {
        return PageRequest.of(pageNumber, pageSize, Sort.unsorted());
    }

    private FlagQueueSort sortOrDefault(FlagQueueSort sort) {
        return sort == null ? FlagQueueSort.NEWEST : sort;
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * The search term as an {@code ILIKE} pattern.
     *
     * <p>Wildcards in what the moderator typed are escaped rather than honoured.
     * They would otherwise be a search language nobody documented — and a
     * moderator searching for a literal {@code 100%} or {@code user_id} would
     * get everything back and no way to tell why.
     */
    private String searchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private FlagStatus parseStatus(String name) {
        try {
            return FlagStatus.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    /**
     * Adds a tallied row to its bucket, ignoring names the enum no longer has.
     * An old row with a retired reason must not cost the whole summary.
     */
    private <E extends Enum<E>> void countInto(
            Map<E, Long> counts,
            String name,
            long total
    ) {
        for (Map.Entry<E, Long> entry : counts.entrySet()) {
            if (entry.getKey().name().equals(name)) {
                counts.merge(entry.getKey(), total, Long::sum);
                return;
            }
        }
    }

    private void validatePagination(
            int pageNumber,
            int pageSize
    ) {

        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0"
            );
        }

        if (pageSize < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be greater than 0"
            );
        }

        if (pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must not exceed 100"
            );
        }
    }

}
