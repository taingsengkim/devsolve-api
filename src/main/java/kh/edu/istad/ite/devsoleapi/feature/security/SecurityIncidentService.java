package kh.edu.istad.ite.devsoleapi.feature.security;

import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.security.dto.SecurityIncidentResponse;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.AttachmentScanContext;
import kh.edu.istad.ite.devsoleapi.feature.virustotal.VirusTotalScanResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SecurityIncidentService {

    /**
     * Records a refused upload so it survives the rejection.
     *
     * <p>Implementations must commit independently of the caller: the
     * transaction that carried the upload is about to be rolled back by the
     * 422, and a row written into it would go with it.
     */
    void record(
            UUID uploaderUserId,
            AttachmentValidator.ValidatedAttachment attachment,
            String sha256,
            VirusTotalScanResponse result,
            AttachmentScanContext context
    );

    /**
     * @param organizationId when set, only incidents against that company.
     *                       Null is the platform-wide view.
     * @param search         free text over uploader handle and email, filename
     *                       and SHA-256
     */
    Page<SecurityIncidentResponse> search(
            UUID organizationId,
            String search,
            VirusTotalScanResponse.Verdict verdict,
            Pageable pageable
    );
}
