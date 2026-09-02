package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RecognitionRepository extends JpaRepository<Recognition, UUID> {

    Page<Recognition> findAllByUserId(UUID userId, Pageable pageable);

    boolean existsByReportId(UUID reportId);

    /**
     * One program's hall of thanks, as one row per researcher and severity.
     *
     * <p>Grouped rather than listed: a thanks page is about who has been
     * credited and how often, not about each award in turn, and a researcher a
     * program has thanked forty times is one row on it. The counts are folded
     * into a ranking in Java because the tie-break needs the severity curve,
     * which only {@code ReputationPolicy} knows how to apply.
     */
    @Query("""
            select recognition.userId as userId,
                   recognition.programId as programId,
                   recognition.severity as severity,
                   count(recognition) as thanks,
                   max(recognition.awardedAt) as lastAwardedAt
            from Recognition recognition
            where recognition.programId = :programId
            group by recognition.userId,
                     recognition.programId,
                     recognition.severity
            """)
    List<ThanksTally> tallyThanksByProgram(
            @Param("programId") UUID programId
    );

    /**
     * The same, across every program the organization runs.
     *
     * <p>Deleted and closed programs included, deliberately. A finding that was
     * credited stays to the researcher's name and the organization's credit
     * after the program that surfaced it is gone — the same rule
     * {@code ReportRepository#countByOrganizationAndState} applies, and
     * dropping them would quietly un-thank people.
     *
     * <p>{@code Recognition} holds the program as a bare id rather than an
     * association, so the join is written out in the where clause.
     */
    @Query("""
            select recognition.userId as userId,
                   recognition.programId as programId,
                   recognition.severity as severity,
                   count(recognition) as thanks,
                   max(recognition.awardedAt) as lastAwardedAt
            from Recognition recognition, Program program
            where program.id = recognition.programId
              and program.organizationId = :organizationId
            group by recognition.userId,
                     recognition.programId,
                     recognition.severity
            """)
    List<ThanksTally> tallyThanksByOrganization(
            @Param("organizationId") UUID organizationId
    );

    interface ThanksTally {

        UUID getUserId();

        /**
         * Which program these thanks came from. Grouped by as well as severity
         * so an organization's board can say where a researcher's count was
         * earned; it costs a row per program a researcher was thanked on,
         * against a board that could not otherwise answer the question at all.
         */
        UUID getProgramId();

        /**
         * Never null on a database whose recognitions carry one, but read as
         * the nullable column it is: severity was added to this table after
         * the fact, and a row the backfill could not price still has none.
         */
        Severity getSeverity();

        long getThanks();

        LocalDateTime getLastAwardedAt();
    }
}
