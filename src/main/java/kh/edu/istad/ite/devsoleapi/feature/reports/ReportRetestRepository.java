package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportRetest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReportRetestRepository
        extends JpaRepository<ReportRetest, UUID> {

    /**
     * The attempt still waiting on the researcher, if there is one.
     *
     * <p>Ordered newest-first and taken as a single result rather than asserted
     * to be unique: the invariant is enforced when a retest is requested, and a
     * read is the wrong place to throw if history somehow disagrees.
     */
    Optional<ReportRetest> findFirstByReportIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
            UUID reportId
    );

    /**
     * The highest attempt number used on a report, or null when it has never
     * been retested. Read rather than counted so that the sequence keeps
     * climbing even if an attempt is ever deleted.
     */
    @Query("""
            select max(retest.attemptNumber)
            from ReportRetest retest
            where retest.report.id = :reportId
            """)
    Integer findHighestAttemptNumber(@Param("reportId") UUID reportId);
}
