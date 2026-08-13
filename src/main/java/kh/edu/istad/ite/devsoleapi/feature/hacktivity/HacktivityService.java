package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto.HacktivityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * The feed is public, so "my hacktivity" and "that researcher's hacktivity"
 * return the same rows for the same id — the difference is only in how the
 * controller resolves the id, and lives there. Same for the two organization
 * endpoints.
 */
public interface HacktivityService {

    Page<HacktivityResponse> findAll(Pageable pageable);

    Page<HacktivityResponse> getUserHacktivity(
            UUID userId,
            Pageable pageable
    );

    Page<HacktivityResponse> getOrganizationHacktivity(
            UUID organizationId,
            Pageable pageable
    );
}
