package kh.edu.istad.ite.devsoleapi.feature.userprofile.service.impl;

import jakarta.ws.rs.NotFoundException;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionService;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserPortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPortfolioServiceImpl implements UserPortfolioService {

    private final UserProfileRepository userProfileRepository;
    private final ProblemService problemService;
    private final SolutionService solutionService;
    private final ShowCasesService showCasesService;
    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ProblemResponse> getProblems(
            UUID userId,
            int pageNumber,
            int pageSize
    ) {
        requirePublicProfile(userId);
        return problemService.findPublishedByAuthor(
                userId,
                PageRequest.of(
                        pageNumber,
                        pageSize,
                        Sort.by(Sort.Direction.DESC, "publishedAt")
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getSolutions(
            UUID userId,
            int pageNumber,
            int pageSize
    ) {
        requirePublicProfile(userId);
        return solutionService.getPublicByAuthor(
                userId,
                pageNumber,
                pageSize
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowCasesSummaryResponse> getShowcases(
            UUID userId,
            int pageNumber,
            int pageSize
    ) {
        requirePublicProfile(userId);
        return showCasesService.getPublishedByAuthor(
                userId,
                pageNumber,
                pageSize
        );
    }

    private void requirePublicProfile(UUID userId) {
        userProfileRepository.findByIdAndStatus(
                        userId,
                        UserStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Public user profile not found"
                ));
    }



    @Override
    public Page<ReportResponse> getReports(UUID userId, int pageNumber, int pageSize) {
        // confirm the profile exists (reuse whatever check getProblems/getSolutions already does)
        userProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User profile not found: " + userId));

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("resolvedAt").descending());

        return reportRepository
                .findByReporterIdAndState(
                        userId,
                        ReportState.RESOLVED,
                        pageable)
                .map(reportMapper::toResponse);
    }

}
