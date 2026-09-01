package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import kh.edu.istad.ite.devsoleapi.feature.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirusTotalContentGuardTest {

    private final VirusTotalGateway gateway = mock(VirusTotalGateway.class);
    private final VirusTotalAlertService alertService =
            mock(VirusTotalAlertService.class);
    private final VirusTotalContentGuard guard = guard(true, false);

    private VirusTotalContentGuard guard(boolean enabled, boolean failOpen) {
        return new VirusTotalContentGuard(
                gateway,
                alertService,
                enabled,
                Duration.ZERO,
                3,
                failOpen,
                duration -> {
                }
        );
    }

    @Test
    void allowsAFileOnlyAfterVirusTotalCompletesCleanly() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(clean("scan-id"));

        guard.requireSafeFile(attachment);

        verify(gateway).submitFile(attachment);
        verify(gateway).getAnalysis("scan-id");
    }

    @Test
    void rejectsMaliciousUrlsWithAnActionable422() {
        when(gateway.submitUrl("https://malicious.example"))
                .thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(
                new VirusTotalScanResponse(
                        "scan-id",
                        "completed",
                        VirusTotalScanResponse.Verdict.MALICIOUS,
                        Map.of("malicious", 4)
                )
        );

        DetailedApiException exception = assertThrows(
                DetailedApiException.class,
                () -> guard.requireSafeUrl("https://malicious.example")
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    @Test
    void failsClosedWhenAnalysisNeverCompletes() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.submitFile(attachment))
                .thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id"))
                .thenReturn(pending("scan-id"));

        VirusTotalUnavailableException exception = assertThrows(
                VirusTotalUnavailableException.class,
                () -> guard.requireSafeFile(attachment)
        );

        assertEquals(504, exception.getStatusCode().value());
    }

    @Test
    void ignoresNonUrlReportTargetsAndDeduplicatesLinks() {
        when(gateway.submitUrl("https://example.com/reference"))
                .thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id"))
                .thenReturn(clean("scan-id"));

        guard.requireSafeUrl("/api/v1/users/1");
        guard.requireSafeUrls(List.of(
                "https://example.com/reference",
                "https://example.com/reference"
        ));

        verify(gateway, never()).submitUrl("/api/v1/users/1");
        verify(gateway).submitUrl("https://example.com/reference");
    }

    @Test
    void failsOpenWhenVirusTotalGivesNoVerdict() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.submitFile(attachment))
                .thenThrow(new VirusTotalUnavailableException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "VirusTotal rate limit was reached"
                ));

        guard(true, true).requireSafeFile(attachment);

        verify(gateway).submitFile(attachment);
    }

    @Test
    void failsOpenWhenAnalysisNeverCompletes() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(pending("scan-id"));

        guard(true, true).requireSafeFile(attachment);

        verify(gateway, times(3)).getAnalysis("scan-id");
    }

    @Test
    void failOpenStillRejectsAMaliciousVerdict() {
        when(gateway.submitUrl("https://malicious.example"))
                .thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(
                new VirusTotalScanResponse(
                        "scan-id",
                        "completed",
                        VirusTotalScanResponse.Verdict.MALICIOUS,
                        Map.of("malicious", 4)
                )
        );

        DetailedApiException exception = assertThrows(
                DetailedApiException.class,
                () -> guard(true, true)
                        .requireSafeUrl("https://malicious.example")
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    @Test
    void checksBeforeWaitingTheFullPollInterval() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        List<Duration> waits = new ArrayList<>();
        VirusTotalContentGuard slow = new VirusTotalContentGuard(
                gateway,
                alertService,
                true,
                Duration.ofSeconds(20),
                3,
                false,
                waits::add
        );
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(clean("scan-id"));

        slow.requireSafeFile(attachment);

        assertEquals(List.of(Duration.ofSeconds(5)), waits);
    }

    @Test
    void disabledGuardNeverCallsVirusTotal() {
        VirusTotalContentGuard disabled = guard(false, false);

        disabled.requireSafeFile(attachment());
        disabled.requireSafeUrl("https://example.com");

        verify(gateway, never()).submitFile(attachment());
        verify(gateway, never()).submitUrl("https://example.com");
    }

    // ------------------------------------------------ the hash fast path

    /**
     * The overwhelming majority of uploads are content VirusTotal has already
     * seen. Those must cost one request and no waiting, or every upload pays
     * for the rare genuinely-new file.
     */
    @Test
    void aKnownHashIsAnsweredWithoutUploadingOrPolling() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any()))
                .thenReturn(Optional.of(clean("known-hash")));

        guard.requireSafeFile(attachment);

        verify(gateway).findByHash(any());
        verify(gateway, never()).submitFile(any());
        verify(gateway, never()).getAnalysis(any());
    }

    @Test
    void aKnownMaliciousHashIsRejectedWithoutUploadingTheFile() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any()))
                .thenReturn(Optional.of(malicious("known-hash")));

        DetailedApiException exception = assertThrows(
                DetailedApiException.class,
                () -> guard.requireSafeFile(attachment)
        );

        assertEquals(422, exception.getStatusCode().value());
        verify(gateway, never()).submitFile(any());
    }

    @Test
    void contentVirusTotalHasNeverSeenFallsBackToSubmitAndPoll() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any())).thenReturn(Optional.empty());
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(clean("scan-id"));

        guard.requireSafeFile(attachment);

        verify(gateway).submitFile(attachment);
        verify(gateway).getAnalysis("scan-id");
    }

    /**
     * The lookup is an optimisation. If it is the thing that is broken, the
     * upload still has a way to reach a verdict.
     */
    @Test
    void aFailedHashLookupFallsBackRatherThanRefusingTheUpload() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any()))
                .thenThrow(new VirusTotalUnavailableException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "VirusTotal rate limit was reached"
                ));
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(clean("scan-id"));

        guard.requireSafeFile(attachment);

        verify(gateway).submitFile(attachment);
    }

    /**
     * The lookup key has to be the digest VirusTotal indexes content by, and
     * it has to be stable: the same bytes asked about twice must ask about the
     * same hash, or the fast path never hits.
     */
    @Test
    void theLookupKeyIsAStableLowercaseSha256OfTheContent() {
        when(gateway.findByHash(any()))
                .thenReturn(Optional.of(clean("known-hash")));

        guard.requireSafeFile(attachment());
        guard.requireSafeFile(attachment());

        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).findByHash(hashes.capture());

        String first = hashes.getAllValues().getFirst();
        assertEquals(first, hashes.getAllValues().get(1));
        assertTrue(
                first.matches("[0-9a-f]{64}"),
                "expected a lowercase hex SHA-256, got: " + first
        );
    }

    // ------------------------------------------------------------- alerting

    @Test
    void aMaliciousFileAlertsBeforeItIsRefused() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        AttachmentScanContext context = new AttachmentScanContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.SECURITY,
                "a report"
        );
        VirusTotalScanResponse verdict = malicious("known-hash");
        when(gateway.findByHash(any())).thenReturn(Optional.of(verdict));

        assertThrows(
                DetailedApiException.class,
                () -> guard.requireSafeFile(attachment, context)
        );

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(alertService).malicious(
                eq(attachment),
                hash.capture(),
                eq(verdict),
                eq(context)
        );

        // The alert carries the digest of what was refused: the file is never
        // stored, so this is the only thing left that identifies it.
        assertTrue(hash.getValue().matches("[0-9a-f]{64}"));
    }

    @Test
    void aCleanFileRaisesNoAlert() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any()))
                .thenReturn(Optional.of(clean("known-hash")));

        guard.requireSafeFile(attachment);

        verify(alertService, never())
                .malicious(any(), any(), any(), any());
    }

    /**
     * No verdict is not a verdict. Nobody is told a file was malicious when
     * VirusTotal never said so — the upload is simply refused.
     */
    @Test
    void anUnscannedFileIsRefusedWithoutClaimingItWasMalicious() {
        AttachmentValidator.ValidatedAttachment attachment = attachment();
        when(gateway.findByHash(any())).thenReturn(Optional.empty());
        when(gateway.submitFile(attachment)).thenReturn(pending("scan-id"));
        when(gateway.getAnalysis("scan-id")).thenReturn(pending("scan-id"));

        assertThrows(
                VirusTotalUnavailableException.class,
                () -> guard.requireSafeFile(attachment)
        );

        verify(alertService, never())
                .malicious(any(), any(), any(), any());
    }

    private AttachmentValidator.ValidatedAttachment attachment() {
        return new AttachmentValidator.ValidatedAttachment(
                "evidence.txt",
                "txt",
                "text/plain",
                "evidence".getBytes(StandardCharsets.UTF_8)
        );
    }

    private VirusTotalScanResponse pending(String id) {
        return new VirusTotalScanResponse(
                id,
                "queued",
                VirusTotalScanResponse.Verdict.PENDING,
                Map.of()
        );
    }

    private VirusTotalScanResponse malicious(String id) {
        return new VirusTotalScanResponse(
                id,
                "completed",
                VirusTotalScanResponse.Verdict.MALICIOUS,
                Map.of("malicious", 58, "undetected", 12)
        );
    }

    private VirusTotalScanResponse clean(String id) {
        return new VirusTotalScanResponse(
                id,
                "completed",
                VirusTotalScanResponse.Verdict.CLEAN,
                Map.of("malicious", 0, "suspicious", 0)
        );
    }
}
