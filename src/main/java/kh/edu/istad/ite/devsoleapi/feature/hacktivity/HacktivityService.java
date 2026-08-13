package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface HacktivityService {

    Page<HacktivityResponse> findAll(Pageable pageable);

    Page<HacktivityResponse> getMyHacktivity(
            UUID userId,
            Pageable pageable
    );

    Page<HacktivityResponse> getUserHacktivity(
            UUID userId,
            Pageable pageable
    );

    Page<HacktivityResponse> getMyOrganizationHacktivity(
            UUID organizationId,
            Pageable pageable
    );

    Page<HacktivityResponse> getOrganizationHacktivity(
            UUID organizationId,
            Pageable pageable
    );

}