package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Every hacktivity entry is rendered with its researcher, organization,
 * report, recognition and program, and all five associations are lazy. Left to
 * itself that is five extra selects per row — fifty-one queries to render a
 * page of ten. The entity graphs fetch the lot in one join. They are safe to
 * combine with {@link Page} because all five are to-one associations: no
 * collection is fetched, so the database still does the paging.
 */
public interface HacktivityRepository extends JpaRepository<Hacktivity, UUID> {

    @Override
    @EntityGraph(attributePaths = {
            "user", "organization", "report", "recognition", "program"
    })
    Page<Hacktivity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "organization", "report", "recognition", "program"
    })
    Page<Hacktivity> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "user", "organization", "report", "recognition", "program"
    })
    Page<Hacktivity> findByOrganizationId(
            UUID organizationId,
            Pageable pageable
    );
}
