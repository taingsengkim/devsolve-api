package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentFlagRepository extends JpaRepository<ContentFlag, UUID> {

    Page<ContentFlag> findByStatus(
            FlagStatus status,
            Pageable pageable
    );

    boolean existsByReporter_IdAndFlaggableTypeAndFlaggableId(
            UUID reporterId,
            FlaggableType flaggableType,
            UUID flaggableId
    );
}
