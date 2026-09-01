package kh.edu.istad.ite.devsoleapi.feature.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SecurityIncidentRepository
        extends JpaRepository<SecurityIncident, UUID>,
        JpaSpecificationExecutor<SecurityIncident> {
}
