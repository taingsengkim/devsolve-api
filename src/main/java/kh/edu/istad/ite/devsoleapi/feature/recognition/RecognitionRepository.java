package kh.edu.istad.ite.devsoleapi.feature.recognition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecognitionRepository extends JpaRepository<Recognition, UUID> {

    Page<Recognition> findByUserIdOrderByAwardedAtDesc(UUID userId, Pageable pageable);
    List<Recognition> findAllByUserId(UUID userId);
}
