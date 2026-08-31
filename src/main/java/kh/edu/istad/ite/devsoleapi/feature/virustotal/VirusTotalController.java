package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.common.attachment.AttachmentValidator;
import kh.edu.istad.ite.devsoleapi.feature.organization.WebsiteUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/virus-total")
public class VirusTotalController {

    private final VirusTotalGateway virusTotalGateway;
    private final AttachmentValidator attachmentValidator;
    private final WebsiteUrlService websiteUrlService;

    @PostMapping(
            value = "/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VirusTotalScanResponse submitFile(
            @RequestPart("file") MultipartFile file
    ) {
        return virusTotalGateway.submitFile(
                attachmentValidator.validateForVirusTotal(file)
        );
    }

    @PostMapping("/urls")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VirusTotalScanResponse submitUrl(
            @Valid @RequestBody VirusTotalUrlScanRequest request
    ) {
        return virusTotalGateway.submitUrl(
                websiteUrlService.normalize(request.url())
        );
    }

    @GetMapping("/analyses/{analysisId}")
    public VirusTotalScanResponse getAnalysis(
            @PathVariable String analysisId
    ) {
        return virusTotalGateway.getAnalysis(analysisId);
    }
}
