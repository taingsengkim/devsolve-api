package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    boolean existsByCweIdIgnoreCase(String cweId);

    boolean existsByCweIdIgnoreCaseAndIdNot(String cweId, UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
