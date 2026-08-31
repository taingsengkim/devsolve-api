package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import java.util.Map;

public record VirusTotalScanResponse(
        String analysisId,
        String status,
        Verdict verdict,
        Map<String, Integer> stats
) {
    public enum Verdict {
        PENDING,
        CLEAN,
        SUSPICIOUS,
        MALICIOUS
    }
}
