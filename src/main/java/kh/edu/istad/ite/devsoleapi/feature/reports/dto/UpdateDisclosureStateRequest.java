package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDisclosureStateRequest {

    @JsonAlias({"disclosureState", "status", "disclosure_status", "disclosure_state"})
    private DisclosureStatus disclosureStatus;
}
