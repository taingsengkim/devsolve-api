package co.istad.ite.devsoleapi.feature.reports.dto;

import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDisclosureStateRequest {

    @JsonAlias({"disclosureState", "status", "disclosure_status", "disclosure_state"})
    private DisclosureStatus disclosureStatus;
}
