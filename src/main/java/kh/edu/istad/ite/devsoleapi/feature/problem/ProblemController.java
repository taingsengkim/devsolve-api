package kh.edu.istad.ite.devsoleapi.feature.problem;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public ResponseEntity<Page<ProblemResponse>> getProblems(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProblemStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(problemService.getProblems(categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> getProblemById(@PathVariable UUID id) {
        return ResponseEntity.ok(problemService.getProblemById(id));
    }

    @PostMapping
    public ResponseEntity<ProblemResponse> createProblem(@Valid @RequestBody ProblemRequest request) {
        UUID authorId = UUID.fromString(AuthUtils.extractUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemService.createProblem(request, authorId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProblemResponse> updateProblem(
            @PathVariable UUID id,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        UUID authorId = UUID.fromString(AuthUtils.extractUserId());
        return ResponseEntity.ok(problemService.updateProblem(id, request, authorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProblem(@PathVariable UUID id) {
        UUID authorId = UUID.fromString(AuthUtils.extractUserId());
        problemService.deleteProblem(id, authorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> incrementViewCount(@PathVariable UUID id) {
        problemService.incrementViewCount(id);
        return ResponseEntity.ok().build();
    }
}