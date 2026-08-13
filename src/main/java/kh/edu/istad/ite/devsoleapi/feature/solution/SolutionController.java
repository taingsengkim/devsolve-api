package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.AcceptedSolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;

    @PostMapping("/problems/{problemId}/solutions")
    public ResponseEntity<SolutionResponse> createSolution(
            @PathVariable UUID problemId,
            @Valid @RequestBody SolutionRequest request
    ) {
        SolutionResponse response = solutionService.createSolution(
                problemId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(response);
    }

    @GetMapping("/problems/{problemId}/solutions")
    public Page<SolutionResponse> getSolutionsByProblemId(
            @PathVariable UUID problemId,
            @RequestParam(defaultValue = "0") @Min(0) int pageNumber,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return solutionService.getSolutionsByProblemId(problemId, pageNumber, pageSize);
    }

    @GetMapping("/solutions/mine")
    public Page<SolutionResponse> getMine(
            @RequestParam(defaultValue = "0") @Min(0) int pageNumber,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return solutionService.getMine(pageNumber, pageSize);
    }

    @GetMapping("/solutions/{id}")
    public ResponseEntity<SolutionResponse> getById(@PathVariable UUID id) {
        return withEtag(solutionService.getById(id));
    }

    @PatchMapping("/solutions/{id}")
    public ResponseEntity<SolutionResponse> updateSolution(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody SolutionUpdateRequest request
    ) {
        return withEtag(solutionService.updateSolution(
                id,
                request,
                parseEtag(ifMatch)
        ));
    }

    @DeleteMapping("/solutions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSolution(@PathVariable UUID id) {
        solutionService.deleteSolution(id);
    }

    @PutMapping("/problems/{problemId}/accepted-solutions")
    public ProblemResponse setAcceptedSolution(
            @PathVariable UUID problemId,
            @Valid @RequestBody AcceptedSolutionRequest request
    ) {
        return solutionService.setAcceptedSolution(problemId, request);
    }

    @DeleteMapping(
            "/problems/{problemId}/accepted-solutions/{solutionId}"
    )
    public ProblemResponse removeAcceptedSolution(
            @PathVariable UUID problemId,
            @PathVariable UUID solutionId
    ) {
        return solutionService.removeAcceptedSolution(problemId, solutionId);
    }

    @PostMapping(
            value = "/solutions/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<SolutionResponse> uploadAttachment(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestPart("file") MultipartFile file
    ) {
        SolutionResponse response = solutionService.uploadAttachment(
                id,
                file,
                parseEtag(ifMatch)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(response);
    }

    @DeleteMapping("/solutions/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch
    ) {
        solutionService.removeAttachment(
                id,
                attachmentId,
                parseEtag(ifMatch)
        );
    }

    @GetMapping("/solutions/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Void> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        URI location = solutionService.createAttachmentDownloadUrl(id, attachmentId);
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(location)
                .build();
    }

    private ResponseEntity<SolutionResponse> withEtag(SolutionResponse response) {
        return ResponseEntity.ok()
                .eTag('"' + Long.toString(response.version()) + '"')
                .body(response);
    }

    private long parseEtag(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2
                && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "If-Match must contain the numeric solution ETag"
            );
        }
    }
}
