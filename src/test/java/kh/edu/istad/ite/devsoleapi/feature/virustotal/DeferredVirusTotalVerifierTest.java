package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeferredVirusTotalVerifierTest {

    private final VirusTotalGateway gateway = mock(VirusTotalGateway.class);
    private final VirusTotalAlertService alertService =
            mock(VirusTotalAlertService.class);
    private final List<Duration> waits = new ArrayList<>();

    private final UUID uploaderId = UUID.randomUUID();

    /**
     * One instance, reused. {@code ValidatedAttachment} carries a byte array,
     * so two records built from the same bytes are not equal to each other and
     * an argument matcher would never match a freshly built one.
     */
    private final AttachmentValidator.ValidatedAttachment attachment =
            new AttachmentValidator.ValidatedAttachment(
                    "evidence.txt",
                    "txt",
                    "text/plain",
                    "evidence".getBytes(StandardCharsets.UTF_8)
            );

    private final AttachmentScanContext context = new AttachmentScanContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            NotificationType.SECURITY,
            "a report"
    );

    /** Runs the task on the calling thread, so the assertions are not racy. */
    private DeferredVirusTotalVerifier verifier(int maxPolls) {
        return new DeferredVirusTotalVerifier(
                gateway,
                alertService,
                Duration.ofSeconds(20),
                maxPolls,
                Runnable::run,
                waits::add
        );
    }

    @Test
    void aVerdictThatTurnsOutBadlyAlertsTheUploadItAlreadyAccepted() {
        when(gateway.getAnalysis("scan-id")).thenReturn(malicious());

        verifier(3).verify(
                pending(),
                attachment,
                "sha256",
                context,
                uploaderId
        );

        verify(alertService).malicious(
                eq(attachment),
                eq("sha256"),
                any(),
                eq(context),
                eq(uploaderId)
        );
    }

    @Test
    void aCleanVerdictTellsNobody() {
        when(gateway.getAnalysis("scan-id")).thenReturn(clean());

        verifier(3).verify(
                pending(),
                attachment,
                "sha256",
                context,
                uploaderId
        );

        verify(alertService, never())
                .malicious(any(), any(), any(), any(), any());
    }

    /**
     * Running out of polls is VirusTotal not answering, which is not an
     * accusation against the file. Nobody is told it was malicious.
     */
    @Test
    void anAnalysisThatNeverFinishesAccusesNobody() {
        when(gateway.getAnalysis("scan-id")).thenReturn(pending());

        verifier(3).verify(
                pending(),
                attachment,
                "sha256",
                context,
                uploaderId
        );

        verify(gateway, times(3)).getAnalysis("scan-id");
        verify(alertService, never())
                .malicious(any(), any(), any(), any(), any());
    }

    /**
     * Nobody is waiting on these, so the gap widens toward the configured
     * interval rather than firing three polls straight into the quota.
     */
    @Test
    void pollsBackOffTowardTheConfiguredInterval() {
        when(gateway.getAnalysis("scan-id")).thenReturn(pending());

        verifier(3).verify(
                pending(),
                attachment,
                "sha256",
                context,
                uploaderId
        );

        assertEquals(
                List.of(
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(20)
                ),
                waits
        );
    }

    /**
     * The upload this belongs to was answered long ago. A failure here is a
     * log line, not an exception thrown at nobody.
     */
    @Test
    void aGatewayFailureDoesNotEscapeTheBackgroundThread() {
        when(gateway.getAnalysis("scan-id"))
                .thenThrow(new VirusTotalUnavailableException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "VirusTotal rate limit was reached"
                ));

        verifier(3).verify(
                pending(),
                attachment,
                "sha256",
                context,
                uploaderId
        );

        verify(alertService, never())
                .malicious(any(), any(), any(), any(), any());
    }

    @Test
    void aFullQueueIsLoggedRatherThanThrownAtTheUploader() {
        DeferredVirusTotalVerifier full = new DeferredVirusTotalVerifier(
                gateway,
                alertService,
                Duration.ZERO,
                3,
                task -> {
                    throw new RejectedExecutionException("queue is full");
                },
                waits::add
        );

        full.verify(pending(), attachment, "sha256", context, uploaderId);

        verify(gateway, never()).getAnalysis(any());
    }

    private VirusTotalScanResponse pending() {
        return new VirusTotalScanResponse(
                "scan-id",
                "queued",
                VirusTotalScanResponse.Verdict.PENDING,
                Map.of()
        );
    }

    private VirusTotalScanResponse malicious() {
        return new VirusTotalScanResponse(
                "scan-id",
                "completed",
                VirusTotalScanResponse.Verdict.MALICIOUS,
                Map.of("malicious", 58)
        );
    }

    private VirusTotalScanResponse clean() {
        return new VirusTotalScanResponse(
                "scan-id",
                "completed",
                VirusTotalScanResponse.Verdict.CLEAN,
                Map.of("malicious", 0)
        );
    }
}
