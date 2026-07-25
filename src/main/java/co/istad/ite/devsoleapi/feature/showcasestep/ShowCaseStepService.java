package co.istad.ite.devsoleapi.feature.showcasestep;

import co.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;

import java.util.List;
import java.util.UUID;

public interface ShowCaseStepService {
    ShowcaseStepResponse create(
            UUID showcaseId,
            CreateShowcaseStepRequest request
    );

    List<ShowcaseStepResponse> getAll(UUID showcaseId);

    ShowcaseStepResponse getById(
            UUID showcaseId,
            UUID stepId
    );

    ShowcaseStepResponse update(
            UUID showcaseId,
            UUID stepId,
            UpdateShowcaseStepRequest request
    );

    void delete(
            UUID showcaseId,
            UUID stepId
    );
}
