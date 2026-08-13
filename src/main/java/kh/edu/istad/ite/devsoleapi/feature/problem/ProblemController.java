package kh.edu.istad.ite.devsoleapi.feature.problem;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public Page<ProblemResponse> findPublished(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) SdlcPhase sdlcPhase,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String technology,
            @PageableDefault(
                    size = 20,
                    sort = "publishedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return problemService.findPublished(
                categoryId,
                sdlcPhase,
                tag,
                technology,
                pageable
        );
    }

    @GetMapping("/mine")
    public Page<ProblemResponse> findMine(
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return problemService.findMine(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemResponse> findById(@PathVariable UUID id) {
        return withEtag(problemService.findById(id));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemResponse createDraft(
            @Valid @RequestBody CreateProblemRequest request
    ) {
        return problemService.createDraft(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemResponse createAndSubmit(
            @Valid @RequestBody CreateProblemRequest request
    ) {
        return problemService.createAndSubmit(request);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProblemResponse> update(
            @PathVariable UUID id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        return withEtag(problemService.update(
                id,
                request,
                parseEtag(ifMatch)
        ));
    }

    @PostMapping("/{id}/submit")
    public ProblemResponse submit(@PathVariable UUID id) {
        return problemService.submit(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable UUID id) {
        problemService.softDelete(id);
    }

    @PostMapping(
            value = "/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemResponse uploadAttachment(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return problemService.uploadAttachment(id, file);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        problemService.removeAttachment(id, attachmentId);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Void> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        URI location = problemService.createAttachmentDownloadUrl(
                id,
                attachmentId
        );
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(location)
                .build();
    }

    @PostMapping("/{id}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void incrementViewCount(@PathVariable UUID id) {
        problemService.incrementViewCount(id);
    }

    private ResponseEntity<ProblemResponse> withEtag(ProblemResponse response) {
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
                    "If-Match must contain the numeric problem ETag"
            );
        }
    }
}
