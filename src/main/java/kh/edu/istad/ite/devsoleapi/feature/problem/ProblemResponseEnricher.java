package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.Vote;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteSummaryProjection;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ProblemResponseEnricher {

    private static final Set<ProblemStatus> AUTHOR_EDITABLE = Set.of(
            ProblemStatus.DRAFT,
            ProblemStatus.REJECTED
    );
    private static final Set<ProblemStatus> ACCEPTABLE = Set.of(
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED
    );

    private final SolutionRepository solutionRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;
    private final BookmarkRepository bookmarkRepository;

    ProblemResponseMetrics read(Problem problem) {
        Optional<UUID> viewerId = currentUserId();
        boolean admin = AuthUtils.hasRole("ADMIN");
        boolean owner = viewerId
                .map(problem.getAuthorId()::equals)
                .orElse(false);

        VoteSummaryProjection votes = voteRepository.summarize(
                VoteType.PROBLEM,
                problem.getId()
        );
        String viewerVote = viewerId.flatMap(id -> voteRepository
                        .findByUserIdAndVotableTypeAndVotableId(
                                id,
                                VoteType.PROBLEM,
                                problem.getId()
                        ))
                .map(Vote::getVoteValue)
                .map(value -> value > 0 ? "UP" : "DOWN")
                .orElse(null);
        boolean bookmarked = viewerId
                .map(id -> bookmarkRepository
                        .existsByUser_IdAndBookmarkableTypeAndBookmarkableId(
                                id,
                                BookmarkType.PROBLEM,
                                problem.getId()
                        ))
                .orElse(false);

        return new ProblemResponseMetrics(
                solutionRepository
                        .countByProblem_IdAndCurrentPublishedRevisionIsNotNullAndDeletedAtIsNull(
                                problem.getId()
                        ),
                commentRepository
                        .countByCommentableTypeAndCommentableIdAndDeletedAtIsNullAndInternalFalse(
                                CommentableType.PROBLEM,
                                problem.getId()
                        ),
                votes == null ? 0 : votes.getScore(),
                bookmarkRepository.countByBookmarkableTypeAndBookmarkableId(
                        BookmarkType.PROBLEM,
                        problem.getId()
                ),
                bookmarked,
                viewerVote,
                owner && AUTHOR_EDITABLE.contains(problem.getStatus()),
                admin || owner && AUTHOR_EDITABLE.contains(problem.getStatus()),
                (admin || owner) && ACCEPTABLE.contains(problem.getStatus())
        );
    }

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
