package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkRepository;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.BookmarkType;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CachedProblem;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.Vote;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteRepository;
import kh.edu.istad.ite.devsoleapi.feature.vote.VoteType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads the counters and viewer-specific flags that hang off a problem but do
 * not live on it.
 *
 * <p>Everything here is loaded per page rather than per problem. Read one
 * problem at a time and a twenty-row listing costs a hundred-odd queries, so
 * {@link #readAll(List)} is the real entry point and the single-problem
 * variant is a thin wrapper over it.
 */
@Component
@RequiredArgsConstructor
class ProblemResponseEnricher {

    private static final Set<ProblemStatus> EDITABLE = Set.of(
            ProblemStatus.DRAFT,
            ProblemStatus.REJECTED,
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED
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
        return readAll(List.of(problem)).getOrDefault(
                problem.getId(),
                ProblemResponseMetrics.empty()
        );
    }

    Map<UUID, ProblemResponseMetrics> readAll(List<Problem> problems) {
        return readAllFor(problems.stream()
                .map(problem -> new Subject(
                        problem.getId(),
                        problem.getAuthorId(),
                        problem.getStatus()
                ))
                .toList());
    }

    /**
     * The same read for problems that came back from the cache rather than the
     * database. A cached response holds no viewer state by construction, so
     * everything this returns is computed fresh for whoever is asking.
     */
    Map<UUID, ProblemResponseMetrics> readAllForCached(
            List<CachedProblem> cached
    ) {
        return readAllFor(cached.stream()
                .map(row -> new Subject(
                        row.response().id(),
                        // Carried alongside the response rather than read off
                        // its author summary, which is null when the profile
                        // has gone missing — that must not cost the author
                        // their own edit rights.
                        row.authorId(),
                        row.response().status()
                ))
                .toList());
    }

    private Map<UUID, ProblemResponseMetrics> readAllFor(
            List<Subject> problems
    ) {
        if (problems.isEmpty()) {
            return Map.of();
        }
        List<UUID> problemIds = problems.stream()
                .map(Subject::id)
                .toList();
        Optional<UUID> viewerId = currentUserId();
        boolean admin = AuthUtils.hasRole("ADMIN");

        Map<UUID, Long> solutionCounts = toTotals(
                solutionRepository.countPublishedByProblemIds(problemIds)
        );
        Map<UUID, Long> commentCounts = toTotals(
                commentRepository.countAllByCommentableIds(
                        CommentableType.PROBLEM,
                        problemIds
                )
        );
        Map<UUID, Long> voteScores = toTotals(
                voteRepository.summarizeAll(VoteType.PROBLEM, problemIds)
        );
        Map<UUID, Long> bookmarkCounts = toTotals(
                bookmarkRepository.countAllByBookmarkableIds(
                        BookmarkType.PROBLEM,
                        problemIds
                )
        );
        Set<UUID> viewerBookmarks = viewerId
                .map(id -> Set.copyOf(bookmarkRepository.findBookmarkedIds(
                        id,
                        BookmarkType.PROBLEM,
                        problemIds
                )))
                .orElseGet(Set::of);
        Map<UUID, Short> viewerVotes = viewerId
                .map(id -> voteRepository
                        .findAllByUserIdAndVotableTypeAndVotableIdIn(
                                id,
                                VoteType.PROBLEM,
                                problemIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Vote::getVotableId,
                                Vote::getVoteValue,
                                (first, second) -> first
                        )))
                .orElseGet(Map::of);

        Map<UUID, ProblemResponseMetrics> metrics = new HashMap<>();
        for (Subject problem : problems) {
            UUID problemId = problem.id();
            boolean owner = problem.authorId() != null
                    && viewerId.map(problem.authorId()::equals).orElse(false);
            long solutionCount = solutionCounts.getOrDefault(problemId, 0L);
            Short vote = viewerVotes.get(problemId);

            metrics.put(problemId, new ProblemResponseMetrics(
                    solutionCount,
                    commentCounts.getOrDefault(problemId, 0L),
                    voteScores.getOrDefault(problemId, 0L),
                    bookmarkCounts.getOrDefault(problemId, 0L),
                    viewerBookmarks.contains(problemId),
                    vote == null ? null : vote > 0 ? "UP" : "DOWN",
                    owner && EDITABLE.contains(problem.status()),
                    admin || owner,
                    (admin || owner) && ACCEPTABLE.contains(problem.status())
            ));
        }
        return metrics;
    }

    /** What computing the metrics needs of a problem, however it was loaded. */
    private record Subject(UUID id, UUID authorId, ProblemStatus status) {
    }

    private Map<UUID, Long> toTotals(List<IdCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(
                IdCountProjection::getId,
                IdCountProjection::getTotal,
                (first, second) -> first
        ));
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
