package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Collects VirusTotal's verdict on content it has never seen before, after the
 * upload that carried it has already been answered.
 *
 * <p>VirusTotal takes minutes on genuinely new content. Waiting for that inside
 * the request meant an upload sat for 95 seconds and was then refused with a
 * 504 — behind a proxy whose own read timeout is 60, so the wait could not even
 * be extended to cover it. Worse, each wait cost up to eight API calls against
 * a quota of four a minute, so the polls that were supposed to find the answer
 * were the reason they got rate limited. Submitting costs one call and the
 * answer arrives here instead.
 *
 * <p>What this does <em>not</em> do is retract the file. The upload has already
 * been stored and answered by the time a verdict lands, so a malicious one is
 * alerted on — administrators and the organization's triage team are notified,
 * and a security incident is recorded — and removed by a person. Known malware
 * never gets this far: {@link VirusTotalContentGuard} answers by hash before
 * anything is stored, and that is what recognises content already known to be
 * dangerous. Only content nobody has ever submitted anywhere reaches this
 * class, and it is accepted pending the answer.
 */
@Service
@Slf4j
public class DeferredVirusTotalVerifier {

    private final VirusTotalGateway gateway;
    private final VirusTotalAlertService alertService;
    private final Duration pollInterval;
    private final int maxPolls;
    private final Executor executor;
    private final VirusTotalContentGuard.Sleeper sleeper;

    @Autowired
    public DeferredVirusTotalVerifier(
            VirusTotalGateway gateway,
            VirusTotalAlertService alertService,
            @Value("${app.virus-total.poll-interval:20s}")
            Duration pollInterval,
            @Value("${app.virus-total.max-polls:6}") int maxPolls
    ) {
        this(
                gateway,
                alertService,
                pollInterval,
                maxPolls,
                defaultExecutor(),
                duration -> Thread.sleep(duration.toMillis())
        );
    }

    DeferredVirusTotalVerifier(
            VirusTotalGateway gateway,
            VirusTotalAlertService alertService,
            Duration pollInterval,
            int maxPolls,
            Executor executor,
            VirusTotalContentGuard.Sleeper sleeper
    ) {
        this.gateway = gateway;
        this.alertService = alertService;
        this.pollInterval = pollInterval;
        this.maxPolls = maxPolls;
        this.executor = executor;
        this.sleeper = sleeper;
    }

    /**
     * Bounded on both axes. These tasks are almost entirely sleep, so a couple
     * of threads carry a lot of them, and a queue that cannot grow without
     * limit is what stops a burst of uploads from turning into unbounded
     * pending work. A rejected task is logged rather than thrown: the upload it
     * belongs to has already been answered, and failing it retrospectively is
     * not something the caller can act on.
     */
    private static Executor defaultExecutor() {
        return new ThreadPoolExecutor(
                1,
                2,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "virustotal-verify");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    /**
     * @param uploaderId read on the request thread and carried in, because the
     *                   security context does not exist on the thread that
     *                   finishes the job — without it every deferred alert
     *                   would name its uploader as "unknown"
     */
    public void verify(
            VirusTotalScanResponse submission,
            AttachmentValidator.ValidatedAttachment attachment,
            String sha256,
            AttachmentScanContext context,
            UUID uploaderId
    ) {

        try {
            executor.execute(() -> await(
                    submission,
                    attachment,
                    sha256,
                    context,
                    uploaderId
            ));
        } catch (RejectedExecutionException rejected) {
            log.error(
                    "No capacity to verify \"{}\" (sha256 {}, analysis {});"
                            + " it stays uploaded and unverified",
                    attachment.originalFileName(),
                    sha256,
                    submission.analysisId()
            );
        }
    }

    private void await(
            VirusTotalScanResponse submission,
            AttachmentValidator.ValidatedAttachment attachment,
            String sha256,
            AttachmentScanContext context,
            UUID uploaderId
    ) {

        try {
            VirusTotalScanResponse result = poll(submission);

            if (result.verdict() == VirusTotalScanResponse.Verdict.PENDING) {
                // Not an accusation. VirusTotal never answered, which is a
                // different thing from answering badly, and only the first is
                // worth telling anybody about.
                log.warn(
                        "VirusTotal never finished analysing \"{}\""
                                + " (sha256 {}, analysis {}); it stays"
                                + " uploaded and unverified",
                        attachment.originalFileName(),
                        sha256,
                        result.analysisId()
                );
                return;
            }

            if (result.verdict() != VirusTotalScanResponse.Verdict.CLEAN) {
                alertService.malicious(
                        attachment,
                        sha256,
                        result,
                        context,
                        uploaderId
                );
            }
        } catch (RuntimeException exception) {
            // Nothing above this catch is a request, so an escape would only
            // reach the executor's default handler and vanish.
            log.error(
                    "Could not verify \"{}\" (sha256 {}, analysis {})",
                    attachment.originalFileName(),
                    sha256,
                    submission.analysisId(),
                    exception
            );
        }
    }

    private VirusTotalScanResponse poll(VirusTotalScanResponse submission) {

        VirusTotalScanResponse result = submission;

        for (int poll = 0;
             result.verdict() == VirusTotalScanResponse.Verdict.PENDING
                     && poll < maxPolls;
             poll++) {

            if (!pause(backoffFor(poll))) {
                return result;
            }
            result = gateway.getAnalysis(result.analysisId());
        }

        return result;
    }

    /**
     * Widens toward the configured interval, the same shape the request path
     * uses for URLs. Nobody is waiting on these, so the interval is free to be
     * long — what it buys is staying under a per-minute quota that the old
     * in-request polling regularly blew through.
     */
    private Duration backoffFor(int completedPolls) {
        return pollInterval.dividedBy(1L << Math.max(0, 2 - completedPolls));
    }

    /** @return false when the wait was interrupted and polling should stop. */
    private boolean pause(Duration delay) {
        try {
            sleeper.sleep(delay);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
