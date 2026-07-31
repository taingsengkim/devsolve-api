package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationNextAction;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;

import java.util.UUID;
import java.time.LocalDateTime;

public record OrganizationVerificationResponse(
        UUID organizationId,
        OrganizationStatus organizationStatus,
        boolean emailVerified,
        OrganizationNextAction nextAction,
        LocalDateTime verificationEmailCanBeResentAt
) {
}
