package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.common.exception.DetailedApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirusTotalContentGuardTest {

    private final VirusTotalGateway gateway = mock(VirusTotalGateway.class);
    private final VirusTotalContentGuard guard = guard(true, false);

    private VirusTotalContentGuard guard(boolean enabled, boolean failOpen) {
        return new VirusTotalContentGuard(
                gateway,
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

    private VirusTotalScanResponse clean(String id) {
        return new VirusTotalScanResponse(
                id,
                "completed",
                VirusTotalScanResponse.Verdict.CLEAN,
                Map.of("malicious", 0, "suspicious", 0)
        );
    }
}
