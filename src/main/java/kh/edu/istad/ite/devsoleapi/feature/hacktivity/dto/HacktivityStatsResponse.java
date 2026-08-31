package kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto;

import java.math.BigDecimal;

/**
 * The four numbers above the feed, counted over the whole stream rather than
 * over whichever page happens to be loaded.
 *
 * <p>Counting them client-side understates every one of them and changes as
 * the reader pages, which is worse than not showing them.
 *
 * @param disclosures    entries on the feed
 * @param researchers    distinct researchers who appear on it
 * @param programsActive distinct programs that appear on it
 * @param totalPaid      summed payouts across every recognised report on it
 * @param currency       the currency {@code totalPaid} is in
 */
public record HacktivityStatsResponse(
        long disclosures,
        long researchers,
        long programsActive,
        BigDecimal totalPaid,
        String currency
) {
}
