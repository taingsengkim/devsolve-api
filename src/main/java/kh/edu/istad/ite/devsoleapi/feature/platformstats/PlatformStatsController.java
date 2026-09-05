package kh.edu.istad.ite.devsoleapi.feature.platformstats;

import kh.edu.istad.ite.devsoleapi.feature.platformstats.dto.PlatformStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PlatformStatsController {

    /**
     * Matches the TTL of the cache behind it, so a shared cache never holds a
     * copy older than the one this application would have served anyway.
     */
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final PlatformStatsService platformStatsService;

    /**
     * The home page's headline figures. Anonymous, and cacheable by anything
     * between here and the browser: the response carries no viewer state at
     * all, so unlike the feeds it needs no {@code Vary: Authorization} and is
     * the same bytes for everybody.
     */
    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getPlatformStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
                .body(platformStatsService.load());
    }
}
