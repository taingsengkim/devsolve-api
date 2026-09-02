package kh.edu.istad.ite.devsoleapi.feature.recognition;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.CreateRecognitionRequest;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.RecognitionResponse;
import kh.edu.istad.ite.devsoleapi.feature.recognition.dto.ThanksResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// Without @Validated the @Min/@Max on the paging parameters below are inert:
// parameter constraints are only enforced on beans the method validation
// post-processor has been told to proxy.
@Validated
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


    /**
     * A program's hall of thanks: the researchers it has publicly credited,
     * most thanked first.
     *
     * <p>Public, like the feed and the leaderboard. The point of thanking
     * somebody is that other people can see it — a hall of thanks nobody may
     * read is a private note.
     *
     * <p>Page and size are plain parameters rather than a {@code Pageable},
     * the same choice {@code LeaderboardController} makes: the ordering is
     * what makes rank mean anything, so it is fixed by the endpoint and not
     * open to a {@code ?sort=} that would renumber the ranks under it.
     */
    @GetMapping("/programs/{programId}/thanks")
    public Page<ThanksResponse> getProgramThanks(
            @PathVariable UUID programId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size
    ) {

        return recognitionService.getProgramThanks(
                programId,
                PageRequest.of(page, size)
        );
    }


    /**
     * The same board across every program an organization runs, including the
     * ones it has since closed — a finding that was credited stays credited.
     */
    @GetMapping("/organizations/{organizationId}/thanks")
    public Page<ThanksResponse> getOrganizationThanks(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size
    ) {

        return recognitionService.getOrganizationThanks(
                organizationId,
                PageRequest.of(page, size)
        );
    }
}
