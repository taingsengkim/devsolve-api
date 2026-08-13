package kh.edu.istad.ite.devsoleapi.feature.reputation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
// Without @Validated the @Min/@Max below are inert annotations: parameter
// constraints are only enforced on beans the method validation post-processor
// has been told to proxy.
@Validated
@RequestMapping("/api/v1/reputation")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * Page and size are taken as plain parameters rather than a {@code
     * Pageable} on purpose: the sort is what makes rank mean anything, so it
     * is fixed by the query and not open to a {@code ?sort=} override.
     */
    @GetMapping("/leaderboard")
    public Page<LeaderboardResponse> getLeaderboard(

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size
    ) {

        return leaderboardService.getLeaderboard(
                PageRequest.of(page, size)
        );
    }
}
