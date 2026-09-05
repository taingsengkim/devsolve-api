package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeaknessRepository extends JpaRepository<Weakness, UUID> {

    Optional<Weakness> findByIdAndIsActiveTrue(UUID id);

    /**
     * The picker a reporter chooses from. Matches the CWE identifier as well
     * as the name so both "89" and "sql" find SQL Injection — a reporter who
     * knows the number should not have to guess the wording, and one who knows
     * neither should not have to scroll a catalog of this size.
     *
     * <p>{@code pattern} is always a complete LIKE pattern, lower-cased by the
     * caller, with any wildcard the searcher typed escaped by {@code !}: an
     * empty search arrives as {@code %} and matches everything. {@code cweId}
     * is nullable, so the name clause is what carries a row that has no
     * identifier yet.
     */
    @Query("""
            select weakness
            from Weakness weakness
            where weakness.isActive = true
              and (lower(weakness.cweId) like :pattern escape '!'
                   or lower(weakness.name) like :pattern escape '!')
            """)
    Page<Weakness> searchActive(
            @Param("pattern") String pattern,
            Pageable pageable
    );

    /**
     * The same search for the administrator managing the catalog, who has to
     * be able to see a deactivated entry in order to bring it back.
     */
    @Query("""
            select weakness
            from Weakness weakness
            where lower(weakness.cweId) like :pattern escape '!'
               or lower(weakness.name) like :pattern escape '!'
            """)
    Page<Weakness> searchAll(
            @Param("pattern") String pattern,
            Pageable pageable
    );

    /**
     * The catalog ordered by how much of it is actually being reported.
     *
     * <p>A left join and a {@code having}, rather than a count per row: the
     * whole catalog is one query either way, and the caller decides with
     * {@code minReports} whether an entry nobody has ever filed under belongs in
     * the answer — 1 for "what do we actually receive", 0 for an administrator
     * auditing the catalog, who needs to see the entries pulling their weight
     * and the ones that are not in the same list.
     *
     * <p>The ordering is fixed here rather than taken from the pageable. It is
     * over an aggregate that only exists inside this query, and a caller's sort
     * would be appended after it and fight it.
     */
    @Query(value = """
            select weakness.id as id,
                   weakness.cweId as cweId,
                   weakness.name as name,
                   weakness.isActive as isActive,
                   count(report.id) as reportCount,
                   sum(case
                           when report.state in :validStates then 1
                           else 0
                       end) as validCount,
                   max(report.submittedAt) as lastReportedAt
            from Weakness weakness
            left join Report report on report.weakness = weakness
            where weakness.isActive = true
            group by weakness.id, weakness.cweId, weakness.name,
                     weakness.isActive
            having count(report.id) >= :minReports
            order by count(report.id) desc, weakness.name asc
            """,
            countQuery = """
            select count(weakness)
            from Weakness weakness
            where weakness.isActive = true
              and (
                  select count(report)
                  from Report report
                  where report.weakness = weakness
              ) >= :minReports
            """)
    Page<WeaknessUsageProjection> findActiveUsage(
            @Param("validStates") Collection<ReportState> validStates,
            @Param("minReports") long minReports,
            Pageable pageable
    );

    /**
     * The same figures for the administrator managing the catalog, who has to
     * see a retired entry to know whether retiring it was right.
     */
    @Query(value = """
            select weakness.id as id,
                   weakness.cweId as cweId,
                   weakness.name as name,
                   weakness.isActive as isActive,
                   count(report.id) as reportCount,
                   sum(case
                           when report.state in :validStates then 1
                           else 0
                       end) as validCount,
                   max(report.submittedAt) as lastReportedAt
            from Weakness weakness
            left join Report report on report.weakness = weakness
            group by weakness.id, weakness.cweId, weakness.name,
                     weakness.isActive
            having count(report.id) >= :minReports
            order by count(report.id) desc, weakness.name asc
            """,
            countQuery = """
            select count(weakness)
            from Weakness weakness
            where (
                select count(report)
                from Report report
                where report.weakness = weakness
            ) >= :minReports
            """)
    Page<WeaknessUsageProjection> findAllUsage(
            @Param("validStates") Collection<ReportState> validStates,
            @Param("minReports") long minReports,
            Pageable pageable
    );

    /**
     * Which of these names the catalog already carries, lower-cased for
     * comparison. Asked once per page of reporter suggestions rather than once
     * per suggestion.
     */
    @Query("""
            select lower(weakness.name)
            from Weakness weakness
            where lower(weakness.name) in :names
            """)
    List<String> findNamesInLowerCase(
            @Param("names") Collection<String> names
    );

    boolean existsByCweIdIgnoreCase(String cweId);

    boolean existsByCweIdIgnoreCaseAndIdNot(String cweId, UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
