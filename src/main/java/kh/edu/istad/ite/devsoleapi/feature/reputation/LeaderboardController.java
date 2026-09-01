package kh.edu.istad.ite.devsoleapi.feature.reputation;

import io.swagger.v3.oas.annotations.Parameter;
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
     *
     * <p>{@code period} defaults to {@code ALL_TIME}, which is what this
     * endpoint has always returned, so a caller that does not send one sees no
     * change.
     */
    @GetMapping("/leaderboard")
    public Page<LeaderboardResponse> getLeaderboard(

            @Parameter(description = "Ranking window: DAY, WEEK, MONTH or "
                    + "ALL_TIME. Windowed boards score the findings resolved "
                    + "inside the window, report recognitionCount as the count "
                    + "of those findings, and leave totalReports and "
                    + "validReports null, since those are only kept as "
                    + "lifetime totals.")
            @RequestParam(defaultValue = "ALL_TIME")
            LeaderboardPeriod period,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be >= 0")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size
    ) {

        return leaderboardService.getLeaderboard(
                period,
                PageRequest.of(page, size)
        );
    }
}
