package kh.edu.istad.ite.devsoleapi.feature.solution;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @PostMapping("/problems/{problemId}/solutions")
    @ResponseStatus(HttpStatus.CREATED)
    public SolutionResponse createSolution(
            @PathVariable UUID problemId,
            @Valid @RequestBody SolutionRequest request
    ) {
        return solutionService.createSolution(problemId, request);
    }

    @GetMapping("/problems/{problemId}/solutions")
    public Page<SolutionResponse> getSolutionsByProblemId(
            @PathVariable UUID problemId,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return solutionService.getSolutionsByProblemId(
                problemId,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/solutions/mine")
    public Page<SolutionResponse> getMine(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return solutionService.getMine(pageNumber, pageSize);
    }

    @GetMapping("/solutions/{id}")
    public SolutionResponse getById(@PathVariable UUID id) {
        return solutionService.getById(id);
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
}
