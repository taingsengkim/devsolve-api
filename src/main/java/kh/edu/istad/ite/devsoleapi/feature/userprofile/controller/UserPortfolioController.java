package kh.edu.istad.ite.devsoleapi.feature.userprofile.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-profiles/{userId}")
public class UserPortfolioController {

    private final UserPortfolioService userPortfolioService;

    @GetMapping("/problems")
    public Page<ProblemResponse> getProblems(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userPortfolioService.getProblems(
                userId,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/solutions")
    public Page<SolutionResponse> getSolutions(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userPortfolioService.getSolutions(
                userId,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/showcases")
    public Page<ShowCasesSummaryResponse> getShowcases(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userPortfolioService.getShowcases(
                userId,
                pageNumber,
                pageSize
        );
    }


    @GetMapping("/reports")
    public Page<ReportResponse> getReports(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userPortfolioService.getReports(
                userId,
                pageNumber,
                pageSize
        );
    }


}

