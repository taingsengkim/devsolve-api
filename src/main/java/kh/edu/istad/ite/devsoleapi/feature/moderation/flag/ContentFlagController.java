package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;


import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flags")
public class ContentFlagController {

    private final ContentFlagService contentFlagService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlagResponse createFlag(
            @Valid @RequestBody CreateFlagRequest request
    ) {
        return contentFlagService.createFlag(request);
    }

    @GetMapping("/mine")
    public Page<FlagResponse> getMyFlags(
            @RequestParam(required = false)
            FlagStatus status,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return contentFlagService.getMyFlags(
                status,
                pageNumber,
                pageSize
        );
    }
}
