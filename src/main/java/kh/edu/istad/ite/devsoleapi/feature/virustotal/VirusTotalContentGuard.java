package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Synchronously gates a user submission on VirusTotal's asynchronous result.
 *
 * <p>Nothing is stored before a verdict exists. Content VirusTotal already
 * recognises is judged by hash in a single request, so the common upload pays
 * no waiting at all; content it has never seen is submitted and polled until it
 * answers. Where no verdict arrives at all — an outage, an exhausted quota, an
 * analysis still running at the last poll — the upload is refused rather than
 * kept, because "not scanned" and "scanned and clean" are not the same file.
 *
 * <p>{@code fail-open} can invert that last rule for an operator who would
 * rather accept unscanned content than refuse uploads during an outage. It is
 * off by default: with it on, the way to get any file past this guard is to
 * upload something VirusTotal has never seen, which is a description of novel
 * malware.
 */
@Service
@Slf4j
public class VirusTotalContentGuard {

    private final VirusTotalGateway gateway;
    private final VirusTotalAlertService alertService;
    private final boolean enabled;
    private final Duration pollInterval;
    private final int maxPolls;
    private final boolean failOpen;
    private final Sleeper sleeper;

    @Autowired
    public VirusTotalContentGuard(
            VirusTotalGateway gateway,
            VirusTotalAlertService alertService,
            @Value("${app.virus-total.enabled:false}") boolean enabled,
            @Value("${app.virus-total.poll-interval:20s}")
            Duration pollInterval,
            @Value("${app.virus-total.max-polls:6}") int maxPolls,
            @Value("${app.virus-total.fail-open:false}") boolean failOpen
    ) {
        this(
                gateway,
                alertService,
                enabled,
                pollInterval,
                maxPolls,
                failOpen,
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    VirusTotalContentGuard(
            VirusTotalGateway gateway,
            VirusTotalAlertService alertService,
            boolean enabled,
            Duration pollInterval,
            int maxPolls,
            boolean failOpen,
            Sleeper sleeper
    ) {
        this.gateway = gateway;
        this.alertService = alertService;
        this.enabled = enabled;
        this.pollInterval = pollInterval;
        this.maxPolls = maxPolls;
        this.failOpen = failOpen;
        this.sleeper = sleeper;
    }

    public void requireSafeFile(
            AttachmentValidator.ValidatedAttachment attachment
    ) {
        requireSafeFile(attachment, AttachmentScanContext.NONE);
    }

    /**
     * Holds the upload until VirusTotal has actually judged the content.
     *
     * <p>The hash is asked about first. VirusTotal answers for content it has
     * seen before straight away, which is the overwhelming majority of what
     * gets uploaded and all of what is already known to be malware — one
     * request, no queue, a verdict in well under a second. Only genuinely new
     * content is submitted and waited on, and that is the case worth waiting
     * for.
     */
    public void requireSafeFile(
            AttachmentValidator.ValidatedAttachment attachment,
            AttachmentScanContext context
    ) {
        if (!enabled) {
            return;
        }

        requireClean(
                () -> knownVerdictFor(attachment)
                        .orElseGet(() -> gateway.submitFile(attachment)),
                "file",
                attachment,
                context
        );
    }

    /**
     * What VirusTotal already knows about these bytes, if anything.
     *
     * <p>A lookup failure is not fatal here: it only means the fast path is
     * unavailable for this upload, and the submit-and-poll path behind it
     * still reaches a verdict. Letting it propagate would turn a degraded
     * optimisation into a refused upload.
     */
    private Optional<VirusTotalScanResponse> knownVerdictFor(
            AttachmentValidator.ValidatedAttachment attachment
    ) {
        try {
            return gateway.findByHash(sha256(attachment.content()));
        } catch (VirusTotalUnavailableException exception) {
            log.debug(
                    "VirusTotal hash lookup was unavailable, falling back to"
                            + " submitting the file: {}",
                    exception.getReason()
            );
            return Optional.empty();
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            // Every JVM ships SHA-256; this cannot happen on a working runtime.
            throw new IllegalStateException(
                    "SHA-256 is not available", exception
            );
        }
    }

    public void requireSafeUrl(String url) {
        if (!enabled || !isHttpUrl(url)) {
            return;
        }
        requireClean(
                () -> gateway.submitUrl(url.trim()),
                "URL",
                null,
                AttachmentScanContext.NONE
        );
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
            String contentType,
            AttachmentValidator.ValidatedAttachment attachment,
            AttachmentScanContext context
    ) {
        VirusTotalScanResponse result;
        try {
            result = awaitVerdict(submission, contentType);
        } catch (VirusTotalUnavailableException exception) {
            if (!failOpen) {
                // No verdict means the content was never judged, and unjudged
                // content is not stored. The status already says whether that
                // was a timeout, a quota, or an outage.
                throw exception;
            }
            // Only reachable where an operator has explicitly turned the
            // guarantee off. Loud enough to notice if it becomes normal.
            log.warn(
                    "Accepting an unscanned {} because VirusTotal gave no"
                            + " verdict and fail-open is on: {}",
                    contentType,
                    exception.getReason()
            );
            return;
        }

        if (result.verdict() != VirusTotalScanResponse.Verdict.CLEAN) {
            if (attachment != null) {
                alertService.malicious(attachment, result, context);
            }
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
