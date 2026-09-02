package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
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
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Gates a user submission on what VirusTotal knows about it.
 *
 * <p>Content VirusTotal already recognises is judged by hash in a single
 * request, before anything is stored. That is the check that catches malware:
 * a file is known to be dangerous precisely because somebody submitted it
 * before. It costs no waiting and it still refuses outright.
 *
 * <p>Content VirusTotal has never seen cannot be judged in the time an HTTP
 * request has. Files are therefore accepted and the verdict collected
 * afterwards by {@link DeferredVirusTotalVerifier}, which alerts on a bad one.
 * URLs are still resolved in line, because a URL submission comes back in
 * seconds rather than minutes.
 *
 * <p>{@code fail-open} governs the remaining case: VirusTotal refusing the
 * submission at all — unreachable, or out of quota — where nothing was scanned
 * and nothing was queued to scan it. Off by default, that refuses the upload.
 */
@Service
@Slf4j
public class VirusTotalContentGuard {

    private final VirusTotalGateway gateway;
    private final VirusTotalAlertService alertService;
    private final DeferredVirusTotalVerifier verifier;
    private final boolean enabled;
    private final Duration pollInterval;
    private final int maxPolls;
    private final boolean failOpen;
    private final Sleeper sleeper;

    @Autowired
    public VirusTotalContentGuard(
            VirusTotalGateway gateway,
            VirusTotalAlertService alertService,
            DeferredVirusTotalVerifier verifier,
            @Value("${app.virus-total.enabled:false}") boolean enabled,
            @Value("${app.virus-total.poll-interval:20s}")
            Duration pollInterval,
            @Value("${app.virus-total.max-polls:6}") int maxPolls,
            @Value("${app.virus-total.fail-open:false}") boolean failOpen
    ) {
        this(
                gateway,
                alertService,
                verifier,
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
            DeferredVirusTotalVerifier verifier,
            boolean enabled,
            Duration pollInterval,
            int maxPolls,
            boolean failOpen,
            Sleeper sleeper
    ) {
        this.gateway = gateway;
        this.alertService = alertService;
        this.verifier = verifier;
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
     * Refuses an upload VirusTotal already knows to be dangerous, and lets
     * everything else through.
     *
     * <p>The hash is asked about first. VirusTotal answers for content it has
     * seen before straight away — one request, no queue, a verdict in well
     * under a second — and that is the answer that matters, because malware is
     * content somebody has submitted before. That check is synchronous and
     * still refuses before a byte is stored.
     *
     * <p>Content VirusTotal has never seen is submitted and then let through,
     * with the verdict collected after the response by
     * {@link DeferredVirusTotalVerifier}. Waiting for it here is what broke:
     * new content takes minutes at VirusTotal, the budget was 95 seconds, the
     * proxy in front of this cuts a request at 60, and the six polls spent
     * trying cost more per upload than a free quota allows in a minute — so
     * uploads failed, and the polling was part of why. A file whose verdict
     * turns out badly is alerted on rather than silently kept: administrators
     * and the organization's triage team are told, and the incident recorded.
     */
    public void requireSafeFile(
            AttachmentValidator.ValidatedAttachment attachment,
            AttachmentScanContext context
    ) {
        if (!enabled) {
            return;
        }

        // Computed once and carried through: it is both the lookup key and,
        // on a refusal, the only identifier of a file that is never stored.
        String sha256 = sha256(attachment.content());

        Optional<VirusTotalScanResponse> known = knownVerdictFor(sha256);
        if (known.isPresent()) {
            rejectIfNotClean(known.get(), "file", attachment, sha256, context);
            return;
        }

        VirusTotalScanResponse submission;
        try {
            submission = gateway.submitFile(attachment);
        } catch (VirusTotalUnavailableException exception) {
            if (!failOpen) {
                // Nothing was scanned and nothing was queued to scan it, which
                // is the outage case fail-open exists for.
                throw exception;
            }
            log.warn(
                    "Accepting an unscanned file because VirusTotal would not"
                            + " take it and fail-open is on: {}",
                    exception.getReason()
            );
            return;
        }

        if (submission.verdict() != VirusTotalScanResponse.Verdict.PENDING) {
            rejectIfNotClean(submission, "file", attachment, sha256, context);
            return;
        }

        verifier.verify(
                submission,
                attachment,
                sha256,
                context,
                currentUserId()
        );
    }


    /**
     * Read here rather than in the verifier: this runs on the request thread,
     * where there is a security context, and the verifier does not.
     */
    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * What VirusTotal already knows about these bytes, if anything.
     *
     * <p>A lookup failure is not fatal here: it only means the fast path is
     * unavailable for this upload, and the submit-and-poll path behind it
     * still reaches a verdict. Letting it propagate would turn a degraded
     * optimisation into a refused upload.
     */
    private Optional<VirusTotalScanResponse> knownVerdictFor(String sha256) {
        try {
            return gateway.findByHash(sha256);
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
            String sha256,
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

        rejectIfNotClean(result, contentType, attachment, sha256, context);
    }


    /**
     * Turns a verdict that is not CLEAN into a 422, telling whoever needs to
     * know on the way out. A CLEAN verdict returns quietly.
     */
    private void rejectIfNotClean(
            VirusTotalScanResponse result,
            String contentType,
            AttachmentValidator.ValidatedAttachment attachment,
            String sha256,
            AttachmentScanContext context
    ) {

        if (result.verdict() == VirusTotalScanResponse.Verdict.CLEAN) {
            return;
        }

        if (attachment != null) {
            alertService.malicious(attachment, sha256, result, context);
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
