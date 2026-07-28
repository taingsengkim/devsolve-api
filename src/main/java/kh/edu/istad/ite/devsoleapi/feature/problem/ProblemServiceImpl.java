package kh.edu.istad.ite.devsoleapi.feature.problem;

// ProblemServiceImpl.java
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProblemServiceImpl implements ProblemService {

    private final ProblemRepository problemRepository;
    private final ProblemMapper problemMapper; // manual mapper

    @Override
    public Page<ProblemResponse> getProblems(UUID categoryId, ProblemStatus status, Pageable pageable) {
        return problemRepository.findActiveProblems(categoryId, status, pageable)
                .map(problemMapper::toResponse);
    }

    @Override
    public ProblemDetailResponse getProblemById(UUID id) {
        Problem problem = problemRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));
        return problemMapper.toDetailResponse(problem);
    }

    @Override
    @Transactional
    public ProblemResponse createProblem(ProblemRequest request, UUID authorId) {
        Problem problem = problemMapper.toEntity(request);
        problem.setAuthorId(authorId);
        problem.setStatus(ProblemStatus.pending_approval);
        problem.setViewCount(0);
        Problem saved = problemRepository.save(problem);
        return problemMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProblemResponse updateProblem(UUID id, ProblemUpdateRequest request, UUID authorId) {
        Problem problem = problemRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (!problem.getAuthorId().equals(authorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the author of this problem");
        }

        if (request.categoryId() != null) {
            problem.setCategoryId(request.categoryId());
        }
        if (request.title() != null) {
            problem.setTitle(request.title());
        }
        if (request.description() != null) {
            problem.setDescription(request.description());
        }

        Problem updated = problemRepository.save(problem);
        return problemMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteProblem(UUID id, UUID authorId) {
        Problem problem = problemRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (!problem.getAuthorId().equals(authorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the author of this problem");
        }

        problemRepository.deleteById(id);

    }

    @Override
    @Transactional
    public void incrementViewCount(UUID id) {
        int updated = problemRepository.incrementViewCount(id);
        if (updated == 0) {
            throw new ResourceNotFoundException("Problem not found with id: " + id);
        }
    }
}