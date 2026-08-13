package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HacktivityRepository extends JpaRepository<Hacktivity, UUID> {
    Page<Hacktivity> findByUserId(UUID userId, Pageable pageable);
    Page<Hacktivity> findByOrganizationId(UUID organizationId, Pageable pageable);
}
