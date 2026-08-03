package kh.edu.istad.ite.devsoleapi.feature.problem;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/problems")
@RequiredArgsConstructor
public class ProblemAdminController {

    private final ProblemService problemService;

    @GetMapping
    public Page<ProblemResponse> findForModeration(
            @RequestParam(required = false) ProblemStatus status,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return problemService.findForModeration(status, pageable);
    }

    @PatchMapping("/{id}/moderation")
    public ProblemResponse moderate(
            @PathVariable UUID id,
            @Valid @RequestBody ProblemModerationRequest request
    ) {
        return problemService.moderate(id, request);
    }
}
