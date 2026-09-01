package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.RetestVerdict;

import java.util.List;
import java.util.UUID;

/**
 * What the researcher found when they re-ran their proof of concept.
 *
 * @param attachmentIds evidence, chosen from the attachments already on this
 *                      report. Uploading is a separate call, so a retest can
 *                      cite a file that was on the report from the start rather
 *                      than forcing a re-upload of it.
 */
public record SubmitRetestRequest(

        @NotNull(message = "A verdict is required")
        RetestVerdict verdict,

        @Size(max = 5000, message = "Notes must not exceed 5000 characters")
        String notes,

        List<UUID> attachmentIds
) {
}
