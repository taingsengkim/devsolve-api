package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;

import java.util.Optional;

public interface VirusTotalGateway {

    /**
     * The verdict VirusTotal already holds for this content, if it holds one.
     *
     * <p>VirusTotal answers for a hash it recognises immediately, from the last
     * time it analysed that content — no upload, no queue, no polling, and one
     * request against the quota instead of four. Only content it has genuinely
     * never seen has to go the slow way round, which is what
     * {@link #submitFile} is for.
     *
     * @param sha256 lowercase hex digest of the file's bytes
     * @return empty when VirusTotal has no record of this content
     */
    Optional<VirusTotalScanResponse> findByHash(String sha256);

    VirusTotalScanResponse submitFile(
            AttachmentValidator.ValidatedAttachment attachment
    );

    VirusTotalScanResponse submitUrl(String url);

    VirusTotalScanResponse getAnalysis(String analysisId);
}
