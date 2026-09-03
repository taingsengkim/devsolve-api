package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportActivityRepository
        extends JpaRepository<ReportActivity, UUID> {

    /**
     * Oldest first, because a timeline is read downwards and the alternative is
     * every client reversing it.
     *
     * <p>The actor is fetched with the rows: a timeline is a list of people
     * doing things, and resolving each name lazily is one query per entry.
     */
    @EntityGraph(attributePaths = "actor")
    List<ReportActivity> findByReport_IdOrderByCreatedAtAsc(UUID reportId);
}
