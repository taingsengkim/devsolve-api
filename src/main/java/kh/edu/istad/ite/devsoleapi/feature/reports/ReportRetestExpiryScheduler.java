package kh.edu.istad.ite.devsoleapi.feature.reports;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Closes retests nobody answered.
 *
 * <p>Without this an unanswered attempt stays open for ever, and because at
 * most one attempt on a report may be open, it blocks every later retest on
 * that report too. The organization's only way out was to triage its own
 * report out of RETESTING, which is a state change it did not want to make.
 *
 * <p>Hourly rather than daily so a deadline is honoured close to the hour it
 * falls on, and off the hour so it does not start alongside every other job
 * that runs at midnight. The sweep is cheap: the query is indexed on exactly
 * the two columns it filters, and on almost every run it returns nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportRetestExpiryScheduler {

    private final ReportService reportService;

    /**
     * Each attempt is lapsed on its own so that one that cannot be closed —
     * a report deleted underneath it, a constraint nobody foresaw — costs that
     * report its expiry and not the whole batch's. Anything that fails is left
     * open and picked up again by the next sweep.
     */
    @Scheduled(cron = "0 20 * * * *")
    public void expireOverdueRetests() {
        List<UUID> overdue = reportService.findOverdueRetestIds();
        if (overdue.isEmpty()) {
            return;
        }

        int expired = 0;
        for (UUID retestId : overdue) {
            try {
                reportService.expireRetest(retestId);
                expired++;
            } catch (RuntimeException exception) {
                log.error(
                        "Could not expire overdue retest {}",
                        retestId,
                        exception
                );
            }
        }
        log.info("Expired {} of {} overdue retests", expired, overdue.size());
    }
}
