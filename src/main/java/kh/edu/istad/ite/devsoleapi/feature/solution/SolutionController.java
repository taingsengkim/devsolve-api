package kh.edu.istad.ite.devsoleapi.feature.solution;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @PostMapping("/{problemId}/solutions")
    @ResponseStatus(HttpStatus.CREATED)
    public SolutionResponse createSolution(
            @PathVariable UUID problemId,
            @Valid @RequestBody SolutionRequest request
    ) {
        return solutionService.createSolution(problemId, request);
    }

    @GetMapping("/{problemId}/solutions")
    public Page<SolutionResponse> getSolutionsByProblemId(
            @PathVariable UUID problemId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return solutionService.getSolutionsByProblemId(problemId, pageable);
    }

    @PatchMapping("/solutions/{id}")
    public SolutionResponse updateSolution(
            @PathVariable UUID id,
            @Valid @RequestBody SolutionUpdateRequest request
    ) {
        return solutionService.updateSolution(id, request);
    }

    @DeleteMapping("/solutions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSolution(@PathVariable UUID id) {
        solutionService.deleteSolution(id);
    }

    @PatchMapping("/solutions/{id}/accept")
    public SolutionResponse acceptSolution(@PathVariable UUID id) {
        return solutionService.acceptSolution(id);
    }
//
//    @PatchMapping("/solutions/{id}/moderate")
//    public SolutionResponse moderateSolution(
//            @PathVariable UUID id,
//            @RequestParam ReviewStatus status,
//            @RequestParam(required = false) String rejectionReason
//    ) {
//        if (!AuthUtils.hasRole("ADMIN")) {
//            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can moderate");
//        }
//        return solutionService.moderateSolution(id, status, rejectionReason);
//    }
}