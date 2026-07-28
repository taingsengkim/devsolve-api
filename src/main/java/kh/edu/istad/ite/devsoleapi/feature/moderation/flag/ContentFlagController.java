package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
