package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagReason;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlaggableType;

import java.util.Map;

/**
 * The numbers on the tabs above the queue.
 *
 * <p>Its own endpoint rather than a field on the page. The three totals are the
 * same whichever page of the queue is open, so folding them into the listing
 * would recompute all of it on every page turn — and a {@code Page} has no room
 * for them anyway without a wrapper that every client then has to unwrap.
 *
 * <p>{@link #byReason} and {@link #byType} describe the open queue only. They
 * are read as "what is waiting for me", so counting closed reports in them
 * would make every breakdown grow forever while the work did not.
 *
 * <p>Every enum value appears in both maps, zeros included: a tab that vanishes
 * when its count reaches zero is a tab a moderator cannot use to check that it
 * reached zero.
 */
public record FlagQueueSummaryResponse(
        long totalPending,

        long totalResolved,

        long totalDismissed,

        Map<FlagReason, Long> byReason,

        Map<FlaggableType, Long> byType
) {
}
