package kh.edu.istad.ite.devsoleapi.feature.hacktivity.dto;

import kh.edu.istad.ite.devsoleapi.feature.hacktivity.HacktivityEventType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.util.List;
import java.util.UUID;

/**
 * The narrowing a caller asked for. Every field is optional and a null or
 * empty one is simply not applied, so the empty filter is the whole feed.
 *
 * <p>This exists so the filters run in the database. A client that can only
 * filter what it has already downloaded is searching one page: a match on
 * page three does not exist to it, which reads as a broken search box rather
 * than as a missing feature.
 *
 * @param userId         one researcher's feed
 * @param organizationId one company's feed
 * @param programId      one program's feed
 * @param q              free text over researcher handle and name, program
 *                       name and report title
 * @param severities     any of, matching the severity enum
 * @param eventTypes     any of, matching the event type enum
 */
public record HacktivityFilter(
        UUID userId,
        UUID organizationId,
        UUID programId,
        String q,
        List<Severity> severities,
        List<HacktivityEventType> eventTypes
) {
}
