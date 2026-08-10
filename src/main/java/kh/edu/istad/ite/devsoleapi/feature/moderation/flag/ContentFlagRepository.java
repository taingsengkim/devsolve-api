package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ContentFlagRepository extends JpaRepository<ContentFlag, UUID> {

    @Query("""
            SELECT flag
            FROM ContentFlag flag
            WHERE (:status IS NULL OR flag.status = :status)
              AND (
                    :flaggableType IS NULL
                    OR flag.flaggableType = :flaggableType
              )
              AND (:reason IS NULL OR flag.reason = :reason)
            """)
    Page<ContentFlag> searchAdminFlags(
            @Param("status") FlagStatus status,
            @Param("flaggableType")
            FlaggableType flaggableType,
            @Param("reason") FlagReason reason,
            Pageable pageable
    );

    @Query("""
            SELECT flag
            FROM ContentFlag flag
            WHERE flag.reporter.id = :reporterId
              AND (:status IS NULL OR flag.status = :status)
            """)
    Page<ContentFlag> findMyFlags(
            @Param("reporterId") UUID reporterId,
            @Param("status") FlagStatus status,
            Pageable pageable
    );

    boolean existsByReporter_IdAndFlaggableTypeAndFlaggableId(
            UUID reporterId,
            FlaggableType flaggableType,
            UUID flaggableId
    );

    long countByStatus(FlagStatus status);
}
