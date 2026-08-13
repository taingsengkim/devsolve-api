package kh.edu.istad.ite.devsoleapi.feature.recognition;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    /**
     * The role check here only proves the caller is staff somewhere. Which
     * organization they may award for is decided in the service, against the
     * program the report was submitted to.
     */
    @PostMapping("/recognitions")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ResponseEntity<RecognitionResponse> awardRecognition(
            @Valid @RequestBody CreateRecognitionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID awardedBy = UUID.fromString(jwt.getSubject());

        RecognitionResponse response =
                recognitionService.awardRecognition(request, awardedBy);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping("/user-profiles/{userId}/recognitions")
    public Page<RecognitionResponse> getRecognitionsByUser(
            @PathVariable UUID userId,

            @PageableDefault(
                    size = 10,
                    sort = "awardedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {

        return recognitionService.getRecognitionsByUser(userId, pageable);
    }
}
