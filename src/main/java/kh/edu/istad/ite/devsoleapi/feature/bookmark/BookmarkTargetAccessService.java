package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookmarkTargetAccessService {

    private static final int PREVIEW_LENGTH = 240;

    private final ProgramRepository programRepository;
    private final ProblemRepository problemRepository;
    private final SolutionRepository solutionRepository;
    private final ShowCasesRepository showCasesRepository;

    public BookmarkTarget requireBookmarkable(
            BookmarkType type,
            UUID targetId
    ) {
        return findBookmarkable(type, targetId).orElseThrow(() ->
                new ResourceNotFoundException(
                        "Public " + type.name().toLowerCase()
                                + " target not found"
                )
        );
    }

    public Optional<BookmarkTarget> findBookmarkable(
            BookmarkType type,
            UUID targetId
    ) {
        return switch (type) {
            case PROGRAM -> programRepository
                    .findByIdAndStateAndSubmissionStateAndVisibilityAndDeletedAtIsNull(
                            targetId,
                            ProgramState.ACTIVE,
                            SubmissionState.APPROVED,
                            Visibility.PUBLIC
                    )
                    .map(program -> new BookmarkTarget(
                            program.getName(),
                            preview(program.getDescription()),
                            null
                    ));
            case PROBLEM -> problemRepository.findPublicById(targetId)
                    .map(problem -> new BookmarkTarget(
                            problem.getTitle(),
                            preview(problem.getDescription()),
                            null
                    ));
            case SOLUTION -> solutionRepository
                    .findByIdAndReviewStatusInAndDeletedAtIsNull(
                            targetId,
                            List.of(
                                    kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus.APPROVED
                            )
                    )
                    .map(solution -> new BookmarkTarget(
                            "Solution for: "
                                    + solution.getProblem().getTitle(),
                            preview(solution.getCurrentPublishedRevision().getBodyMarkdown()),
                            null
                    ));
            case SHOWCASE -> showCasesRepository
                    .findByIdAndReviewStatusAndDeletedAtIsNull(
                            targetId,
                            ReviewStatus.APPROVED
                    )
                    .map(showcase -> new BookmarkTarget(
                            showcase.getTitle(),
                            preview(showcase.getOverview()),
                            showcase.getCoverImageUrl()
                    ));
        };
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LENGTH - 1) + "…";
    }
}
