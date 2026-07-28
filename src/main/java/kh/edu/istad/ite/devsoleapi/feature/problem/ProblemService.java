package kh.edu.istad.ite.devsoleapi.feature.problem;

// ProblemService.java

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProblemService {
    Page<ProblemResponse> getProblems(UUID categoryId, ProblemStatus status, Pageable pageable);
    ProblemDetailResponse getProblemById(UUID id);
    ProblemResponse createProblem(ProblemRequest request, UUID authorId);
    ProblemResponse updateProblem(UUID id, ProblemUpdateRequest request, UUID authorId);
    void deleteProblem(UUID id, UUID authorId);
    void incrementViewCount(UUID id);
}