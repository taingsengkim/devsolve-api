package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.Solution;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoteTargetAccessService {

    private static final List<ReviewStatus> PUBLIC_SOLUTION_STATUSES =
            List.of(ReviewStatus.APPROVED);

    private final ProblemRepository problemRepository;
    private final SolutionRepository solutionRepository;
    private final CommentService commentService;
    private final ShowCasesRepository showCasesRepository;

    @Transactional(readOnly = true)
    public VoteTarget requireVotable(VoteType type, UUID targetId) {
        return switch (type) {
            case PROBLEM -> {
                Problem problem = problemRepository
                        .findPublicById(targetId)
                        .orElseThrow(this::targetNotFound);
                yield new VoteTarget(problem.getAuthorId());
            }
            case SOLUTION -> {
                Solution solution = solutionRepository
                        .findByIdAndReviewStatusInAndDeletedAtIsNull(
                                targetId,
                                PUBLIC_SOLUTION_STATUSES
                        )
                        .orElseThrow(this::targetNotFound);
                yield new VoteTarget(solution.getAuthorId());
            }
            case COMMENT -> {
                CommentResponse comment = commentService.findById(targetId);
                // A tombstone is a placeholder holding a thread together, not
                // something anybody can still agree or disagree with.
                if (comment.isInternal() || comment.isRemoved()) {
                    throw targetNotFound();
                }
                yield new VoteTarget(comment.getAuthorId());
            }
            case SHOWCASE -> {
                ShowCases showcase = showCasesRepository
                        .findByIdAndReviewStatusAndDeletedAtIsNull(
                                targetId,
                                kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                        )
                        .orElseThrow(this::targetNotFound);
                yield new VoteTarget(showcase.getAuthor().getId());
            }
        };
    }

    private ResourceNotFoundException targetNotFound() {
        return new ResourceNotFoundException("Votable target not found");
    }
}
