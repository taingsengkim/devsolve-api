package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ShowCasesService {
    Page<ShowCasesResponse> getAllPublished(int pageNumber, int pageSize);

    ShowCasesResponse getById(UUID id);
//
    ShowCasesResponse create(CreateShowCasesRequest request);
//
    ShowCasesResponse update(UUID showcaseId,
                             UpdateShowCasesRequest request);

    void softDelete(UUID showcaseId);
//
    void hardDelete(UUID showcaseId);

    ShowCasesResponse updateStatus(
            UUID showcaseId,
            UpdateShowcaseStatusRequest request
    );

}



