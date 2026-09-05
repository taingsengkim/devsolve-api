package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.vote.Vote;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteSummaryProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowcaseDetailEnricherTest {

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private FollowRepository followRepository;

    private ShowcaseDetailEnricher enricher;

    private final UUID showcaseId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        enricher = new ShowcaseDetailEnricher(
                showCasesRepository,
                showcaseRevisionRepository,
                voteRepository,
                bookmarkRepository,
                followRepository
        );

        lenient().when(voteRepository.summarize(VoteType.SHOWCASE, showcaseId))
                .thenReturn(votes(4L, 6L, 2L));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void countsAreShownToASignedOutReaderAndViewerStateIsNot() {
        when(bookmarkRepository.countByBookmarkableTypeAndBookmarkableId(
                BookmarkType.SHOWCASE,
                showcaseId
        )).thenReturn(9L);
        when(followRepository.countByFollowableTypeAndFollowableId(
                FollowType.SHOWCASE,
                showcaseId
        )).thenReturn(3L);
        when(followRepository.countByFollowableTypeAndFollowableId(
                FollowType.USER,
                authorId
        )).thenReturn(21L);

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertEquals(4L, enriched.engagement().voteScore());
        assertEquals(6L, enriched.engagement().upvoteCount());
        assertEquals(2L, enriched.engagement().downvoteCount());
        assertEquals(9L, enriched.engagement().bookmarkCount());
        assertEquals(3L, enriched.engagement().followerCount());

        assertNull(enriched.viewer().vote());
        assertFalse(enriched.viewer().bookmarked());
        assertFalse(enriched.viewer().canEdit());
        assertFalse(enriched.author().followedByViewer());

        // The four per-viewer lookups have a known answer for a reader with no
        // token, and running them anyway is four round trips per page view.
        verify(voteRepository, never()).findByUserIdAndVotableTypeAndVotableId(
                any(),
                any(),
                any()
        );
        verify(followRepository, never())
                .existsByFollower_IdAndFollowableTypeAndFollowableId(
                        any(),
                        any(),
                        any()
                );
        verifyNoInteractions(showcaseRevisionRepository);
    }

    @Test
    void theFollowOnTheAuthorIsReadOnceAndAnsweredInBothPlaces() {
        UUID viewerId = UUID.randomUUID();
        authenticate(viewerId);
        when(followRepository
                .existsByFollower_IdAndFollowableTypeAndFollowableId(
                        viewerId,
                        FollowType.USER,
                        authorId
                )).thenReturn(true);

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertTrue(enriched.author().followedByViewer());
        assertTrue(enriched.viewer().followingAuthor());
        verify(followRepository, times(1))
                .existsByFollower_IdAndFollowableTypeAndFollowableId(
                        viewerId,
                        FollowType.USER,
                        authorId
                );
    }

    @Test
    void aVoteOfMinusOneReadsAsDown() {
        UUID viewerId = UUID.randomUUID();
        authenticate(viewerId);
        when(voteRepository.findByUserIdAndVotableTypeAndVotableId(
                viewerId,
                VoteType.SHOWCASE,
                showcaseId
        )).thenReturn(Optional.of(Vote.builder()
                .userId(viewerId)
                .votableType(VoteType.SHOWCASE)
                .votableId(showcaseId)
                .voteValue((short) -1)
                .build()));

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertEquals("DOWN", enriched.viewer().vote());
    }

    @Test
    void theAuthorIsToldTheirEditIsStillWaitingOnAModerator() {
        authenticate(authorId);
        when(showcaseRevisionRepository.existsByShowcase_Id(showcaseId))
                .thenReturn(true);

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertTrue(enriched.viewer().owner());
        assertTrue(enriched.viewer().canEdit());
        assertTrue(enriched.viewer().canDelete());
        assertTrue(enriched.viewer().editUnderReview());
    }

    @Test
    void aStrangerIsNotToldThatAnEditIsPending() {
        authenticate(UUID.randomUUID());

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertFalse(enriched.viewer().owner());
        assertFalse(enriched.viewer().canDelete());
        assertFalse(enriched.viewer().editUnderReview());
        verify(showcaseRevisionRepository, never())
                .existsByShowcase_Id(any());
    }

    @Test
    void theAuthorCardCountsOnlyWhatAVisitorCouldOpen() {
        when(showCasesRepository
                .countByAuthor_IdAndReviewStatusAndDeletedAtIsNull(
                        authorId,
                        ReviewStatus.APPROVED
                )).thenReturn(7L);
        when(followRepository.countByFollowableTypeAndFollowableId(
                FollowType.USER,
                authorId
        )).thenReturn(21L);

        ShowCasesResponse enriched = enricher.apply(showcase(), response());

        assertEquals(authorId, enriched.author().id());
        assertEquals("sokdara", enriched.author().username());
        assertEquals(140, enriched.author().reputation());
        assertEquals(7L, enriched.author().publishedShowcaseCount());
        assertEquals(21L, enriched.author().followerCount());
    }

    private ShowCases showcase() {
        UserProfile author = new UserProfile();
        author.setId(authorId);
        author.setUsername("sokdara");
        author.setFullName("Sok Dara");
        author.setAvatarUrl("https://cdn.test/avatar.png");
        author.setBiography("Breaks caches for a living.");
        author.setReputation(140);

        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);
        showcase.setAuthor(author);
        showcase.setReviewStatus(ReviewStatus.APPROVED);
        return showcase;
    }

    private ShowCasesResponse response() {
        return ShowCasesResponse.builder().id(showcaseId).build();
    }

    private VoteSummaryProjection votes(
            long score,
            long upvotes,
            long downvotes
    ) {
        return new VoteSummaryProjection() {
            @Override
            public long getScore() {
                return score;
            }

            @Override
            public long getUpvotes() {
                return upvotes;
            }

            @Override
            public long getDownvotes() {
                return downvotes;
            }
        };
    }

    private void authenticate(UUID subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
