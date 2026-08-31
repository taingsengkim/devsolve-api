package kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityEventType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the public hacktivity feed.
 *
 * <p>Everything a card renders is on the row. The feed is read far more than
 * it is written and a client cannot make a second call per row without turning
 * one page into fifty requests, so the handles, slugs, weakness and payout
 * that the cards need are joined in here rather than left to be looked up.
 *
 * <p>{@code createdAt} is an {@link Instant}: it goes on the wire as ISO-8601
 * in UTC with an explicit {@code Z}. The column behind it is a local timestamp,
 * so it is converted through the server's own zone — the zone it was written
 * in — rather than being relabelled as UTC. An offset-less timestamp is what
 * silently shifts every "14 minutes ago" on a client in another zone.
 */
public record HacktivityResponse(

        UUID id,

        HacktivityEventType eventType,

        User user,

        Organization organization,

        Program program,

        Report report,

        Recognition recognition,

        /**
         * Null when the recognised report carries no payout at all. An award
         * of points only comes back with a null {@code amount} and a non-null
         * {@code points}, which is a different thing from no reward.
         */
        Reward reward,

        Instant createdAt
) {

    /**
     * {@code username} is the handle a profile URL is built from and
     * {@code fullName} is what a card prints. Both are always set: the column
     * behind each is NOT NULL.
     */
    public record User(
            UUID id,
            String username,
            String fullName,
            String avatarUrl,
            Integer reputation
    ) {}

    public record Organization(
            UUID id,
            String name,
            String slug,
            String logoUrl
    ) {}

    public record Program(
            UUID id,
            String name,
            String handle
    ) {}

    /**
     * {@code severity} is the enum, not a string, so its casing cannot drift:
     * it is always one of NONE, LOW, MEDIUM, HIGH, CRITICAL. It is nullable
     * because a database trigger clears it while a severity dispute is open.
     *
     * <p>{@code disclosureStatus} says whether the title is safe to print.
     * A row is on the feed because it was recognised, which is not the same as
     * being disclosed, so a client that prints titles unconditionally is
     * publishing the titles of undisclosed findings.
     */
    public record Report(
            UUID id,
            String title,
            Severity severity,
            DisclosureStatus disclosureStatus,
            Weakness weakness
    ) {}

    /** Null when the report was never classified against the catalogue. */
    public record Weakness(
            String cweId,
            String name
    ) {}

    public record Recognition(
            UUID id,
            String title,
            String description
    ) {}

    /**
     * The total across every payout on the recognised report, not one of them:
     * a report can be paid more than once and a card shows one number.
     *
     * <p>{@code currency} is a constant. The platform has no per-payout
     * currency column, so every amount it has ever stored is in one currency,
     * and saying which one on the row is more useful to a client than leaving
     * it to be assumed.
     */
    public record Reward(
            BigDecimal amount,
            String currency,
            Integer points
    ) {}
}
