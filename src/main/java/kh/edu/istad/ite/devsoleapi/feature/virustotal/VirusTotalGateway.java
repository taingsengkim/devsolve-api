package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;

public interface VirusTotalGateway {

    VirusTotalScanResponse submitFile(
            AttachmentValidator.ValidatedAttachment attachment
    );

    VirusTotalScanResponse submitUrl(String url);

    VirusTotalScanResponse getAnalysis(String analysisId);
}
