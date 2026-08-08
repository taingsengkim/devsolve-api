package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import org.springframework.web.multipart.MultipartFile;

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

    ShowcaseStepResponse uploadImage(
            UUID showcaseId,
            UUID stepId,
            ShowcaseStepImageKind kind,
            MultipartFile file
    );

    ShowcaseStepResponse removeImage(
            UUID showcaseId,
            UUID stepId,
            ShowcaseStepImageKind kind
    );

    void delete(
            UUID showcaseId,
            UUID stepId
    );
}
