package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseAuthorResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseEngagementResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewerResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.vote.Vote;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteBreakdownProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteSummaryProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The counters and viewer flags that hang off a showcase but do not live on it:
 * what the rest of the platform has done with it, and what the person currently
 * reading has done and may do.
 *
 * <p>Kept out of {@link ShowCasesMapper} because the mapper also serves the
 * review queue, the revision paths and every write. A submission still under
 * review has no votes to count and no author card to draw.
 *
 * <p>None of this can be cached: half of it is per-viewer by definition, and
 * the other half — the counters — is exactly the part of a page that must not
 * lag behind the button the reader just pressed. That is also why it runs
 * <em>after</em> the listing cache rather than inside it.
 *
 * <p>{@link #applyToPage} reads a whole page per query rather than a card at a
 * time; a page of twenty done row by row is a hundred and forty round trips.
 */
@Component
@RequiredArgsConstructor
class ShowcaseEnricher {

    private final ShowCasesRepository showCaseRepository;
    private final ShowcaseRevisionRepository showcaseRevisionRepository;
    private final VoteRepository voteRepository;
    private final BookmarkRepository bookmarkRepository;
    private final FollowRepository followRepository;

    // ----------------------------------------------------------- one showcase

    ShowCasesResponse applyToDetail(
            ShowCases showcase,
            ShowCasesResponse response
    ) {
        UserProfile author = showcase.getAuthor();
        Optional<UUID> viewerId = currentUserId();

        // Read once and handed to both blocks: the author card renders it as
        // the state of a follow button and the viewer block as a fact about
        // the reader, but it is one row either way.
        boolean followsAuthor = author != null
                && viewerId.map(viewer -> followRepository
                        .existsByFollower_IdAndFollowableTypeAndFollowableId(
                                viewer,
                                FollowType.USER,
                                author.getId()
                        ))
                .orElse(false);

        return response.toBuilder()
                .author(author == null ? null : authorCard(author, followsAuthor))
                .engagement(engagement(showcase.getId()))
                .viewer(viewer(showcase, viewerId, followsAuthor))
                .build();
    }

    private ShowcaseAuthorResponse authorCard(
            UserProfile author,
            boolean followedByViewer
    ) {
        return new ShowcaseAuthorResponse(
                author.getId(),
                author.getUsername(),
                author.getFullName(),
                author.getAvatarUrl(),
                author.getBiography(),
                author.getReputation(),
                showCaseRepository
                        .countByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
                                author.getId(),
                                ReviewStatus.APPROVED
                        ),
                followRepository.countByFollowableTypeAndFollowableId(
                        FollowType.USER,
                        author.getId()
                ),
                followedByViewer
        );
    }

    private ShowcaseEngagementResponse engagement(UUID showcaseId) {
        VoteSummaryProjection votes = voteRepository.summarize(
                VoteType.SHOWCASE,
                showcaseId
        );

        return new ShowcaseEngagementResponse(
                votes == null ? 0L : votes.getScore(),
                votes == null ? 0L : votes.getUpvotes(),
                votes == null ? 0L : votes.getDownvotes(),
                bookmarkRepository.countByBookmarkableTypeAndBookmarkableId(
                        BookmarkType.SHOWCASE,
                        showcaseId
                ),
                followRepository.countByFollowableTypeAndFollowableId(
                        FollowType.SHOWCASE,
                        showcaseId
                )
        );
    }

    /**
     * A signed-out reader short-circuits to the all-false shape rather than
     * running four lookups whose answer is already known.
     */
    private ShowcaseViewerResponse viewer(
            ShowCases showcase,
            Optional<UUID> viewerId,
            boolean followsAuthor
    ) {
        if (viewerId.isEmpty()) {
            return ShowcaseViewerResponse.anonymous();
        }

        UUID viewer = viewerId.get();
        UUID showcaseId = showcase.getId();
        boolean owner = showcase.getAuthor() != null
                && showcase.getAuthor().getId().equals(viewer);

        return new ShowcaseViewerResponse(
                direction(voteRepository
                        .findByUserIdAndVotableTypeAndVotableId(
                                viewer,
                                VoteType.SHOWCASE,
                                showcaseId
                        )
                        .map(Vote::getVoteValue)
                        .orElse(null)),
                bookmarkRepository
                        .existsByUser_IdAndBookmarkableTypeAndBookmarkableId(
                                viewer,
                                BookmarkType.SHOWCASE,
                                showcaseId
                        ),
                followRepository
                        .existsByFollower_IdAndFollowableTypeAndFollowableId(
                                viewer,
                                FollowType.SHOWCASE,
                                showcaseId
                        ),
                followsAuthor,
                owner,
                // Both the same today: editing a published showcase opens a
                // revision rather than touching it, and the delete paths admit
                // the author and nobody else. They are separate fields because
                // a page binds a button to each, and the two will not move
                // together forever.
                owner,
                owner,
                owner && showcaseRevisionRepository
                        .existsByShowcase_Id(showcaseId)
        );
    }

    // ------------------------------------------------------------- whole page

    /**
     * Fills the same two blocks on a page of cards, seven queries for the page
     * rather than seven per card — and only three of them for a signed-out
     * reader, whose viewer state is known without asking.
     *
     * <p>{@code editUnderReview} is the one field left false here: it is the
     * author's own view of their pending edit, and a card already carries
     * {@code hasUnpublishedRevision} for that.
     */
    Page<ShowCasesSummaryResponse> applyToPage(
            Page<ShowCasesSummaryResponse> page
    ) {
        if (page.isEmpty()) {
            return page;
        }

        List<UUID> showcaseIds = page.getContent().stream()
                .map(ShowCasesSummaryResponse::id)
                .toList();
        List<UUID> authorIds = page.getContent().stream()
                .map(card -> parseUuid(card.authorId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Optional<UUID> viewerId = currentUserId();

        Map<UUID, VoteBreakdownProjection> tallies = voteRepository
                .summarizeAllDetailed(VoteType.SHOWCASE, showcaseIds)
                .stream()
                .collect(Collectors.toMap(
                        VoteBreakdownProjection::getId,
                        Function.identity(),
                        (first, second) -> first
                ));
        Map<UUID, Long> bookmarkCounts = toTotals(
                bookmarkRepository.countAllByBookmarkableIds(
                        BookmarkType.SHOWCASE,
                        showcaseIds
                )
        );
        Map<UUID, Long> followerCounts = toTotals(
                followRepository.countByFollowableIds(
                        FollowType.SHOWCASE,
                        showcaseIds
                )
        );

        Map<UUID, Short> viewerVotes = viewerId
                .map(viewer -> voteRepository
                        .findAllByUserIdAndVotableTypeAndVotableIdIn(
                                viewer,
                                VoteType.SHOWCASE,
                                showcaseIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Vote::getVotableId,
                                Vote::getVoteValue,
                                (first, second) -> first
                        )))
                .orElseGet(Map::of);
        Set<UUID> viewerBookmarks = idsOf(viewerId, viewer ->
                bookmarkRepository.findBookmarkedIds(
                        viewer,
                        BookmarkType.SHOWCASE,
                        showcaseIds
                ));
        Set<UUID> followedShowcases = idsOf(viewerId, viewer ->
                followRepository.findFollowedIds(
                        viewer,
                        FollowType.SHOWCASE,
                        showcaseIds
                ));
        Set<UUID> followedAuthors = authorIds.isEmpty()
                ? Set.of()
                : idsOf(viewerId, viewer -> followRepository.findFollowedIds(
                        viewer,
                        FollowType.USER,
                        authorIds
                ));

        return page.map(card -> {
            UUID showcaseId = card.id();
            UUID authorId = parseUuid(card.authorId());
            VoteBreakdownProjection tally = tallies.get(showcaseId);
            boolean owner = authorId != null
                    && viewerId.map(authorId::equals).orElse(false);

            return card.toBuilder()
                    .engagement(new ShowcaseEngagementResponse(
                            tally == null ? 0L : tally.getScore(),
                            tally == null ? 0L : tally.getUpvotes(),
                            tally == null ? 0L : tally.getDownvotes(),
                            bookmarkCounts.getOrDefault(showcaseId, 0L),
                            followerCounts.getOrDefault(showcaseId, 0L)
                    ))
                    .viewer(viewerId.isEmpty()
                            ? ShowcaseViewerResponse.anonymous()
                            : new ShowcaseViewerResponse(
                                    direction(viewerVotes.get(showcaseId)),
                                    viewerBookmarks.contains(showcaseId),
                                    followedShowcases.contains(showcaseId),
                                    authorId != null
                                            && followedAuthors.contains(authorId),
                                    owner,
                                    owner,
                                    owner,
                                    false
                            ))
                    .build();
        });
    }

    // ---------------------------------------------------------------- shared

    /**
     * The stored {@code short} as the two words a client can read, so nobody
     * downstream has to know that 1 and -1 are the only legal values.
     */
    private String direction(Short voteValue) {
        if (voteValue == null) {
            return null;
        }
        return voteValue > 0 ? "UP" : "DOWN";
    }

    private Set<UUID> idsOf(
            Optional<UUID> viewerId,
            Function<UUID, List<UUID>> query
    ) {
        return viewerId
                .map(viewer -> Set.copyOf(query.apply(viewer)))
                .orElseGet(Set::of);
    }

    private Map<UUID, Long> toTotals(List<IdCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                IdCountProjection::getId,
                IdCountProjection::getTotal,
                (first, second) -> first
        ));
    }

    /**
     * A summary carries its author as a string. Anything unparseable costs the
     * card its ownership flags rather than the whole page.
     */
    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * The viewer, or empty for a signed-out one. Deliberately not
     * {@link AuthUtils#extractUserId()}: these run on public endpoints, where
     * having no token is the normal case and not a 401.
     */
    private Optional<UUID> currentUserId() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
