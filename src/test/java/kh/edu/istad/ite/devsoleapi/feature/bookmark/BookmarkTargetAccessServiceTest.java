package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.Solution;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRevision;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkTargetAccessServiceTest {

    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private ShowCasesRepository showCasesRepository;

    @Test
    void programMustBeActiveApprovedAndPublic() {
        UUID programId = UUID.randomUUID();
        Program program = new Program();
        program.setId(programId);
        program.setName("Security Research Program");
        program.setDescription("Public program description");
        when(programRepository
                .findByIdAndStateAndSubmissionStateAndVisibility(
                        programId,
                        ProgramState.ACTIVE,
                        SubmissionState.APPROVED,
                        Visibility.PUBLIC
                ))
                .thenReturn(Optional.of(program));

        BookmarkTarget result = service().requireBookmarkable(
                BookmarkType.PROGRAM,
                programId
        );

        assertEquals("Security Research Program", result.title());
    }

    @Test
    void problemMustBePublic() {
        UUID problemId = UUID.randomUUID();
        when(problemRepository.findPublicById(problemId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().requireBookmarkable(
                        BookmarkType.PROBLEM,
                        problemId
                )
        );
    }

    @Test
    void solutionMustBeApprovedOrAccepted() {
        UUID solutionId = UUID.randomUUID();
        Problem problem = Problem.builder()
                .title("How do I secure JWT authentication?")
                .description("Question details")
                .build();
        Solution solution = Solution.builder().build();
        solution.setId(solutionId);
        solution.setProblem(problem);
        SolutionRevision revision = SolutionRevision.builder()
                .solution(solution)
                .revisionNumber(1)
                .summary("Validate JWT claims")
                .bodyMarkdown("Validate issuer and audience claims.")
                .approachType(kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType.FIX)
                .moderationStatus(kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus.APPROVED)
                .build();
        solution.setCurrentPublishedRevision(revision);
        when(solutionRepository
                .findByIdAndReviewStatusInAndDeletedAtIsNull(
                        solutionId,
                        List.of(
                                kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus.APPROVED
                        )
                ))
                .thenReturn(Optional.of(solution));

        BookmarkTarget result = service().requireBookmarkable(
                BookmarkType.SOLUTION,
                solutionId
        );

        assertEquals(
                "Solution for: How do I secure JWT authentication?",
                result.title()
        );
    }

    @Test
    void showcaseReturnsCoverImageForSavedItemsScreen() {
        UUID showcaseId = UUID.randomUUID();
        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);
        showcase.setTitle("Smart Traffic Monitor");
        showcase.setOverview("A live traffic dashboard");
        showcase.setCoverImageUrl("https://example.com/traffic.jpg");
        when(showCasesRepository
                .findByIdAndReviewStatusAndDeletedAtIsNull(
                        showcaseId,
                        ReviewStatus.APPROVED
                ))
                .thenReturn(Optional.of(showcase));

        BookmarkTarget result = service().requireBookmarkable(
                BookmarkType.SHOWCASE,
                showcaseId
        );

        assertEquals(
                "https://example.com/traffic.jpg",
                result.imageUrl()
        );
    }

    private BookmarkTargetAccessService service() {
        return new BookmarkTargetAccessService(
                programRepository,
                problemRepository,
                solutionRepository,
                showCasesRepository
        );
    }
}
