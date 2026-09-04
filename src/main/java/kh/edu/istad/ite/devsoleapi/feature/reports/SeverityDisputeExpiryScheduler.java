package kh.edu.istad.ite.devsoleapi.feature.reports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Settles severity disagreements the reporter never answered.
 *
 * <p>Without this, asking the reporter first would be strictly worse than
 * sending every disagreement to an administrator: a report with no agreed
 * severity cannot be resolved, rewarded or retested, so one researcher who
 * stops reading their notifications would freeze that report for good.
 *
 * <p>Silence settles at the triage severity rather than escalating. Escalating
 * would hand administrators exactly the queue this step exists to spare them,
 * and it would be a strange thing to do on the reporter's behalf — nobody is
 * arguing.
 *
 * <p>Half past the hour, so it does not start alongside the retest sweep on the
 * twentieth minute or anything else that runs on the hour.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeverityDisputeExpiryScheduler {

    private final ReportService reportService;

    @Scheduled(cron = "0 30 * * * *")
    public void settleUnansweredSeverityDisputes() {
        List<UUID> overdue = reportService.findOverdueSeverityDisputeIds();
        if (overdue.isEmpty()) {
            return;
        }

        int settled = 0;
        for (UUID disputeId : overdue) {
            try {
                reportService.autoAcceptTriageSeverity(disputeId);
                settled++;
            } catch (RuntimeException exception) {
                // Left for the next sweep. An unsettled disagreement is the
                // state it was already in.
                log.error(
                        "Could not settle unanswered severity dispute {}",
                        disputeId,
                        exception
                );
            }
        }
        log.info(
                "Settled {} of {} unanswered severity disputes",
                settled,
                overdue.size()
        );
    }
}
