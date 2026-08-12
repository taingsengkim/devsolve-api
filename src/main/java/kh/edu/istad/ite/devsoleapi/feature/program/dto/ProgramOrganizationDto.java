package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The organization behind a program, as a researcher browsing publicly is
 * allowed to see it.
 *
 * <p>Deliberately narrower than {@code OrganizationResponse}: the review trail
 * ({@code status}, {@code rejectionReason}, {@code reviewedBy}) and the details
 * collected during registration ({@code ownerJobTitle}, {@code joiningReason})
 * are internal and must not travel on a public endpoint. {@code verifiedAt}
 * carries the only part of that state a reader needs — whether the organization
 * has been vetted — without exposing where an unapproved one stands.
 */
public record ProgramOrganizationDto(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String websiteUrl,
        String description,
        Industry industry,
        String country,
        LocalDateTime verifiedAt
) {
}
