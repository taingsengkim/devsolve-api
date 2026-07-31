package kh.edu.istad.ite.devsoleapi.feature.recognition;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/recognitions")
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionService recognitionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN')")
    public ResponseEntity<RecognitionResponse> awardRecognition(
            @Valid @RequestBody CreateRecognitionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID awardedBy = UUID.fromString(jwt.getSubject());

        RecognitionResponse response = recognitionService.awardRecognition(request, awardedBy);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}