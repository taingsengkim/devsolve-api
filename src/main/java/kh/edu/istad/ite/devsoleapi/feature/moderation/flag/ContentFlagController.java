package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;


import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flags")
public class ContentFlagController {
    private final ContentFlagService service;

    @PostMapping
    public FlagResponse createFlag(
            @Valid @RequestBody CreateFlagRequest request
    ) {
        return service.createFlag(request);
    }

}
