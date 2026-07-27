package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WeaknessRepository extends JpaRepository<Weakness, UUID> {

    Optional<Weakness> findByIdAndIsActiveTrue(UUID id);
}
