package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The author, counters and viewer vote that hang off a solution.
 *
 * <p>Loaded per page rather than per solution. Read one at a time and a
 * twenty-row listing costs eighty-odd queries, which is what this replaces.
 */
@Component
@RequiredArgsConstructor
class SolutionResponseEnricher {

    private final UserProfileRepository userProfileRepository;
    private final VoteRepository voteRepository;
    private final CommentRepository commentRepository;

    record SolutionMetrics(
            UserProfile author,
            long voteScore,
            String viewerVote,
            long commentCount
    ) {
    }

    Map<UUID, SolutionMetrics> readAll(List<Solution> solutions) {
        if (solutions.isEmpty()) {
            return Map.of();
        }
        List<UUID> solutionIds = solutions.stream()
                .map(Solution::getId)
                .toList();
        Set<UUID> authorIds = solutions.stream()
                .map(Solution::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserProfile> authors = userProfileRepository
                .findAllById(authorIds)
                .stream()
                .collect(Collectors.toMap(
                        UserProfile::getId,
                        Function.identity()
                ));
        Map<UUID, Long> voteScores = toTotals(
                voteRepository.summarizeAll(VoteType.SOLUTION, solutionIds)
        );
        Map<UUID, Long> commentCounts = toTotals(
                commentRepository.countAllByCommentableIds(
                        CommentableType.SOLUTION,
                        solutionIds
                )
        );
        Map<UUID, Short> viewerVotes = currentUserId()
                .map(userId -> voteRepository
                        .findAllByUserIdAndVotableTypeAndVotableIdIn(
                                userId,
                                VoteType.SOLUTION,
                                solutionIds
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Vote::getVotableId,
                                Vote::getVoteValue,
                                (first, second) -> first
                        )))
                .orElseGet(Map::of);

        Map<UUID, SolutionMetrics> metrics = new HashMap<>();
        for (Solution solution : solutions) {
            UUID solutionId = solution.getId();
            Short vote = viewerVotes.get(solutionId);
            metrics.put(solutionId, new SolutionMetrics(
                    authors.get(solution.getAuthorId()),
                    voteScores.getOrDefault(solutionId, 0L),
                    vote == null ? null : vote > 0 ? "UP" : "DOWN",
                    commentCounts.getOrDefault(solutionId, 0L)
            ));
        }
        return metrics;
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
