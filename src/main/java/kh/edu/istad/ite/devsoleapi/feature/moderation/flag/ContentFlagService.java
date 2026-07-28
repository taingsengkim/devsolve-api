package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContentFlagService {

    FlagResponse createFlag(
            CreateFlagRequest request
    );

    Page<FlagResponse> getAdminFlags(
            int pageNumber,
            int pageSize
    );

    FlagResponse dismissFlag(UUID id);

}
