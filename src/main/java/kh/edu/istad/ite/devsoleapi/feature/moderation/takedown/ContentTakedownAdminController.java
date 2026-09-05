package kh.edu.istad.ite.devsoleapi.feature.moderation.takedown;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.takedown.dto.TakedownRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Taking community content down, in one place.
 *
 * <p>Five endpoints rather than one that takes a type parameter, because a
 * client that can reach {@code /admin/showcases/{id}/takedown} cannot reach the
 * wrong kind of content by mistyping an enum, and each path documents itself in
 * the API listing.
 *
 * <p>{@code POST … /takedown} rather than {@code DELETE …} for two reasons: the
 * reason has to travel in a body, which not every client and proxy will send on
 * a DELETE, and the response is the audit record rather than nothing. The
 * existing {@code DELETE /api/v1/admin/programs/{id}} is left alone; it does
 * the same removal without recording why.
 *
 * <p>Every path here sits under {@code /api/v1/admin}, which the security
 * configuration already restricts to the ADMIN role. The service checks the
 * role again rather than trusting that, because it is also reachable from flag
 * resolution.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class ContentTakedownAdminController {

    private final ContentTakedownService contentTakedownService;

    @ApiResponse(
            responseCode = "404",
            description = "The content does not exist, or has already been "
                    + "removed.",
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @PostMapping("/problems/{id}/takedown")
    public ModerationActionResponse takeDownProblem(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownRequest request
    ) {
        return contentTakedownService.takeDown(
                ModerationTargetType.PROBLEM,
                id,
                request.reason()
        );
    }

    @PostMapping("/showcases/{id}/takedown")
    public ModerationActionResponse takeDownShowcase(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownRequest request
    ) {
        return contentTakedownService.takeDown(
                ModerationTargetType.SHOWCASE,
                id,
                request.reason()
        );
    }

    @PostMapping("/solutions/{id}/takedown")
    public ModerationActionResponse takeDownSolution(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownRequest request
    ) {
        return contentTakedownService.takeDown(
                ModerationTargetType.SOLUTION,
                id,
                request.reason()
        );
    }

    /**
     * A comment with live replies keeps its row and loses its text, so the
     * replies underneath — written by other people — survive their parent
     * being removed.
     */
    @PostMapping("/comments/{id}/takedown")
    public ModerationActionResponse takeDownComment(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownRequest request
    ) {
        return contentTakedownService.takeDown(
                ModerationTargetType.COMMENT,
                id,
                request.reason()
        );
    }

    @PostMapping("/programs/{id}/takedown")
    public ModerationActionResponse takeDownProgram(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownRequest request
    ) {
        return contentTakedownService.takeDown(
                ModerationTargetType.PROGRAM,
                id,
                request.reason()
        );
    }
}
