package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Synchronously gates a user submission on VirusTotal's asynchronous result.
 * The deliberately small poll count respects the public API's tight quota.
 */
@Service
@Slf4j
public class VirusTotalContentGuard {

    private final VirusTotalGateway gateway;
    private final boolean enabled;
    private final Duration pollInterval;
    private final int maxPolls;
    private final boolean failOpen;
    private final Sleeper sleeper;

    @Autowired
    public VirusTotalContentGuard(
            VirusTotalGateway gateway,
            @Value("${app.virus-total.enabled:false}") boolean enabled,
            @Value("${app.virus-total.poll-interval:20s}")
            Duration pollInterval,
            @Value("${app.virus-total.max-polls:3}") int maxPolls,
            @Value("${app.virus-total.fail-open:true}") boolean failOpen
    ) {
        this(
                gateway,
                enabled,
                pollInterval,
                maxPolls,
                failOpen,
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    VirusTotalContentGuard(
            VirusTotalGateway gateway,
            boolean enabled,
            Duration pollInterval,
            int maxPolls,
            boolean failOpen,
            Sleeper sleeper
    ) {
        this.gateway = gateway;
        this.enabled = enabled;
        this.pollInterval = pollInterval;
        this.maxPolls = maxPolls;
        this.failOpen = failOpen;
        this.sleeper = sleeper;
    }

    public void requireSafeFile(
            AttachmentValidator.ValidatedAttachment attachment
    ) {
        if (!enabled) {
            return;
        }
        requireClean(() -> gateway.submitFile(attachment), "file");
    }

    public void requireSafeUrl(String url) {
        if (!enabled || !isHttpUrl(url)) {
            return;
        }
        requireClean(() -> gateway.submitUrl(url.trim()), "URL");
    }

    public void requireSafeUrls(Collection<String> urls) {
        if (!enabled || urls == null || urls.isEmpty()) {
            return;
        }
        Set<String> uniqueUrls = new LinkedHashSet<>(urls);
        uniqueUrls.forEach(this::requireSafeUrl);
    }

    private void requireClean(
            Supplier<VirusTotalScanResponse> submission,
            String contentType
    ) {
        VirusTotalScanResponse result;
        try {
            result = awaitVerdict(submission, contentType);
        } catch (VirusTotalUnavailableException exception) {
            if (!failOpen) {
                throw exception;
            }
            // Deliberately not fatal: on a public key a burst of uploads hits
            // the per-minute quota routinely, and refusing every upload while
            // that lasts is a worse outcome than storing content VirusTotal
            // declined to look at. Loud enough to notice if it becomes normal.
            log.warn(
                    "Accepting an unscanned {} because VirusTotal gave no"
                            + " verdict: {}",
                    contentType,
                    exception.getReason()
            );
            return;
        }

        if (result.verdict() != VirusTotalScanResponse.Verdict.CLEAN) {
            throw new DetailedApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "VirusTotal rejected the submitted " + contentType,
                    Map.of(
                            "analysisId", result.analysisId(),
                            "verdict", result.verdict(),
                            "stats", result.stats()
                    )
            );
        }
    }

    private VirusTotalScanResponse awaitVerdict(
            Supplier<VirusTotalScanResponse> submission,
            String contentType
    ) {
        VirusTotalScanResponse result = submission.get();
        for (int poll = 0;
             result.verdict() == VirusTotalScanResponse.Verdict.PENDING
                     && poll < maxPolls;
             poll++) {
            pause(backoffFor(poll));
            result = gateway.getAnalysis(result.analysisId());
        }

        if (result.verdict() == VirusTotalScanResponse.Verdict.PENDING) {
            throw new VirusTotalUnavailableException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "VirusTotal did not finish scanning the " + contentType
                            + " (analysis " + result.analysisId() + ")"
            );
        }
        return result;
    }

    /**
     * Waits a fraction of the configured interval before the first check and
     * grows toward the full one after that.
     *
     * <p>A flat interval made every upload sit out the whole wait even when
     * VirusTotal already had a verdict ready, because a submission is always
     * reported as PENDING. Content VirusTotal recognises now clears in a
     * quarter of the time, while a genuinely new file still gets a widening
     * gap between polls rather than three rapid ones against the quota.
     */
    private Duration backoffFor(int completedPolls) {
        return pollInterval.dividedBy(1L << Math.max(0, 2 - completedPolls));
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "VirusTotal scan was interrupted",
                    exception
            );
        }
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://")
                || normalized.startsWith("http://");
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
