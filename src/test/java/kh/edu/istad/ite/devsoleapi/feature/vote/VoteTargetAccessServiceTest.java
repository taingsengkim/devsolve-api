package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.comments.dto.CommentResponse;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.Solution;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteTargetAccessServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private CommentService commentService;

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Test
    void publishedProblemIsVotable() {
        UUID targetId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Problem problem = Problem.builder()
                .id(targetId)
                .authorId(authorId)
                .build();
        when(problemRepository.findPublicById(targetId))
                .thenReturn(Optional.of(problem));

        VoteTarget target = service().requireVotable(
                VoteType.PROBLEM,
                targetId
        );

        assertEquals(authorId, target.authorId());
    }

    @Test
    void solutionMustBeApprovedOrAccepted() {
        UUID targetId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Solution solution = Solution.builder()
                .id(targetId)
                .authorId(authorId)
                .build();
        when(solutionRepository
                .findByIdAndReviewStatusInAndDeletedAtIsNull(
                        targetId,
                        List.of(ReviewStatus.APPROVED)
                ))
                .thenReturn(Optional.of(solution));

        VoteTarget target = service().requireVotable(
                VoteType.SOLUTION,
                targetId
        );

        assertEquals(authorId, target.authorId());
    }

    @Test
    void internalCommentIsNotVotable() {
        UUID targetId = UUID.randomUUID();
        when(commentService.findById(targetId))
                .thenReturn(CommentResponse.builder()
                        .id(targetId)
                        .commentableType(CommentableType.REPORT)
                        .commentableId(UUID.randomUUID())
                        .authorId(UUID.randomUUID())
                        .content("Internal note")
                        .internal(true)
                        .build());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().requireVotable(
                        VoteType.COMMENT,
                        targetId
                )
        );
    }

    @Test
    void onlyApprovedShowcaseIsVotable() {
        UUID targetId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UserProfile author = new UserProfile();
        author.setId(authorId);
        ShowCases showcase = new ShowCases();
        showcase.setId(targetId);
        showcase.setAuthor(author);
        when(showCasesRepository
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        targetId,
                        kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                ))
                .thenReturn(Optional.of(showcase));

        VoteTarget target = service().requireVotable(
                VoteType.SHOWCASE,
                targetId
        );

        assertEquals(authorId, target.authorId());
        verify(showCasesRepository)
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        targetId,
                        kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus.APPROVED
                );
    }

    @Test
    void missingTargetReturnsNotFound() {
        UUID targetId = UUID.randomUUID();
        when(problemRepository.findPublicById(targetId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().requireVotable(
                        VoteType.PROBLEM,
                        targetId
                )
        );
    }

    private VoteTargetAccessService service() {
        return new VoteTargetAccessService(
                problemRepository,
                solutionRepository,
                commentService,
                showCasesRepository
        );
    }
}
