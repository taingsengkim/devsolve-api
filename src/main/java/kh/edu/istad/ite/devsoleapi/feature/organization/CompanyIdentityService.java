package kh.edu.istad.ite.devsoleapi.feature.organization;

import java.util.UUID;

public interface CompanyIdentityService {

    RegisteredCompany register(
            String email,
            String fullName,
            String password
    );

    boolean isEmailVerified(UUID companyUserId);

    void delete(RegisteredCompany company);
}
