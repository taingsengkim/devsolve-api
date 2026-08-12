package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HacktivityRepository extends JpaRepository<Hacktivity, UUID> {
}
