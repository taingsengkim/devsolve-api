package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContentFlagService {
    FlagResponse createFlag(
            CreateFlagRequest request
    );

    Page<FlagResponse> getMyFlags(
            FlagStatus status,
            int pageNumber,
            int pageSize
    );

    Page<FlagResponse> getAdminFlags(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            int pageNumber,
            int pageSize
    );

    FlagResponse getAdminFlagById(UUID id);

    FlagResponse dismissFlag(
            UUID id
    );

    FlagResponse resolveFlag(
            UUID id,
            ResolveFlagRequest request
    );

}
