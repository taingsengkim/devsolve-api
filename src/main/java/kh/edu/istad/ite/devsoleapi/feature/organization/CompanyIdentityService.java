package kh.edu.istad.ite.devsoleapi.feature.organization;

import java.util.UUID;
import java.util.Set;

public interface CompanyIdentityService {

    RegisteredCompany register(
            String email,
            String fullName,
            String password
    );

    boolean isEmailVerified(UUID companyUserId);

    void sendVerificationEmail(UUID companyUserId);

    Set<UUID> findUserIdsByRealmRole(String role);

    void delete(RegisteredCompany company);
}
