package kh.edu.istad.ite.devsoleapi.feature.userprofile.service;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface UserPortfolioService {

    Page<ProblemResponse> getProblems(
            UUID userId,
            int pageNumber,
            int pageSize
    );

    Page<SolutionResponse> getSolutions(
            UUID userId,
            int pageNumber,
            int pageSize
    );

    Page<ShowCasesSummaryResponse> getShowcases(
            UUID userId,
            int pageNumber,
            int pageSize
    );
}
