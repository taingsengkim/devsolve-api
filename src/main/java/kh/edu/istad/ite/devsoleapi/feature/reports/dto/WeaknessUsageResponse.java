package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A weakness class with how often it is reported.
 *
 * @param reportCount every report filed under it, whatever became of it
 * @param validCount  the ones triage agreed with — confirmed, retesting or
 *                    resolved. Never larger than {@code reportCount}; the gap
 *                    between the two is the noise this class attracts
 * @param share       {@code reportCount} as a percentage of every classified
 *                    report on the platform, to one decimal place. Computed
 *                    against classified reports only, so an unclassified backlog
 *                    cannot make a class look rarer than it is
 */
public record WeaknessUsageResponse(
        UUID id,
        String cweId,
        String name,
        Boolean isActive,
        long reportCount,
        long validCount,
        double share,
        LocalDateTime lastReportedAt
) {
}
