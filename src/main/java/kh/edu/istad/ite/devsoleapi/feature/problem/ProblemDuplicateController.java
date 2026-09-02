package kh.edu.istad.ite.devsoleapi.feature.problem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The considered half of "has somebody already asked this?".
 *
 * <p>Its own controller rather than another method on {@link ProblemController}
 * because it is the only endpoint in the API that spends a third-party quota
 * per call, and that is worth being able to see in one file — along with the
 * rate limit and the fallback that go with it.
 *
 * <p>Authenticated, by the {@code /api/v1/problems/**} rule in the security
 * chain. The keyword sibling at {@code GET /api/v1/problems/related} is
 * anonymous and stays that way; this one is metered, so it needs somebody to
 * meter.
 */
@RestController
@RequestMapping("/api/v1/problems")
@RequiredArgsConstructor
public class ProblemDuplicateController {

    private final ProblemDuplicateService problemDuplicateService;

    @Operation(
            summary = "Check a problem draft against what is already published",
            description = """
                    Retrieves candidates by keyword, then has a model read them \
                    against the draft and say which are duplicates, which are \
                    the same bug in a different setting, and which are merely \
                    worth reading. Solved problems come first.

                    A POST because a description does not belong in a URL — it \
                    changes nothing, and calling it twice is free apart from \
                    the rate limit.

                    Answers `aiReviewed: false` with plain keyword matches when \
                    the model is not configured or could not be reached; it \
                    never fails for that reason. An empty `suggestions` list is \
                    a normal answer and means nothing similar was found.

                    Rate limited per account. Call it when the author asks or \
                    before submitting — for suggestions while typing, use \
                    `GET /api/v1/problems/related`, which is free and answers \
                    between keystrokes.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Suggestions, possibly empty")
    @ApiResponse(responseCode = "400", description = "Missing or over-long title")
    @ApiResponse(responseCode = "429", description = "Too many checks from this account")
    @PostMapping("/duplicate-check")
    public DuplicateCheckResponse check(
            @Valid @RequestBody DuplicateCheckRequest request
    ) {
        return problemDuplicateService.check(request);
    }
}
