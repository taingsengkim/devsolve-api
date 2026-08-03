package kh.edu.istad.ite.devsoleapi.feature.organization;

import java.util.UUID;

public record OrganizationLifecycleEvent(
        OrganizationLifecycleEventType type,
        UUID organizationId,
        UUID ownerId,
        String organizationName,
        int submissionVersion,
        String reason
) {
}
