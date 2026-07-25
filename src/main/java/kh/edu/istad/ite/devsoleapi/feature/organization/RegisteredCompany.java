package kh.edu.istad.ite.devsoleapi.feature.organization;

import java.util.UUID;

public record RegisteredCompany(
        UUID id,
        String email,
        String fullName
) {
}
