package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import java.util.UUID;

/**
 * Who reported it, as much as a moderator needs to weigh the report.
 *
 * <p>Reputation is here for one reason: a report from an account with a
 * history reads differently from the fourth report of the morning from an
 * account created yesterday. It is context for a human, not a score anything
 * acts on.
 *
 * <p>The whole object is null when the content filter raised the flag rather
 * than a person.
 */
public record FlagReporterSummary(
        UUID id,
        String name,
        String avatarUrl,
        Integer reputation
) {
}
