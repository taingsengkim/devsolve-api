package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseAuthorResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseEngagementResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewerResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteSummaryProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Everything a showcase detail shows that does not live on the showcase row:
 * who wrote it, what the rest of the platform has done with it, and what the
 * person currently reading it has done and may do.
 *
 * <p>Kept out of {@link ShowCasesMapper} because the mapper also serves the
 * review queue, the revision paths and every write. A submission still under
 * review has no votes to count and no author card to draw, and paying for
 * those reads to answer "your edit was saved" would be six queries nobody
 * looks at.
 *
 * <p>None of this can be cached: half of it is per-viewer by definition, and
 * the other half — the counters — is exactly the part of a page that must not
 * lag behind the button the reader just pressed.
 */
@Component
@RequiredArgsConstructor
class ShowcaseDetailEnricher {

    private final ShowCasesRepository showCaseRepository;
    private final ShowcaseRevisionRepository showcaseRevisionRepository;
    private final VoteRepository voteRepository;
    private final BookmarkRepository bookmarkRepository;
    private final FollowRepository followRepository;

    ShowCasesResponse apply(
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
                voteRepository
                        .findByUserIdAndVotableTypeAndVotableId(
                                viewer,
                                VoteType.SHOWCASE,
                                showcaseId
                        )
                        .map(vote -> vote.getVoteValue() > 0 ? "UP" : "DOWN")
                        .orElse(null),
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

    /**
     * The viewer, or empty for a signed-out one. Deliberately not
     * {@link AuthUtils#extractUserId()}: this runs on a public endpoint, where
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
