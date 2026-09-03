package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportActivityType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry in a report's history, oldest first.
 *
 * @param actor     null when the platform acted rather than a person, which
 *                  today means a retest that lapsed on its deadline
 * @param fromState both this and {@code toState} are null on an event that did
 *                  not move the report
 * @param detail    one line of context written by the platform. Never free text
 *                  somebody typed — that is a comment, and lives on the
 *                  discussion where it can be answered.
 */
public record ReportActivityResponse(
        UUID id,
        ReportActivityType activityType,
        ReportResponse.ActorSummary actor,
        ReportState fromState,
        ReportState toState,
        Severity severity,
        String detail,
        LocalDateTime createdAt
) {
}
