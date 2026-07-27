package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;

public record UpdateDisclosureStateRequest(
        @NotNull(message = "Disclosure status is required")
        @JsonAlias({
                "disclosureState",
                "status",
                "disclosure_status",
                "disclosure_state"
        })
        DisclosureStatus disclosureStatus
) {
}
