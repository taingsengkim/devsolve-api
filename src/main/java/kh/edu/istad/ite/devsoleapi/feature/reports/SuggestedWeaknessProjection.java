package kh.edu.istad.ite.devsoleapi.feature.reports;

import java.time.LocalDateTime;

/**
 * A class reporters named themselves, and how many of them named it.
 *
 * <p>The catalog only ever showed what an administrator had already thought of.
 * What reporters typed when nothing in it fit was kept on the report and read by
 * one triager — so the platform held the evidence of its own gaps and had no way
 * to look at it. Grouped case-insensitively on the trimmed text, because "SSRF",
 * "ssrf " and "Ssrf" are one gap being reported three times, not three.
 */
public interface SuggestedWeaknessProjection {

    /**
     * The lower-cased, trimmed text the grouping is on — the key, and what to
     * compare against the catalog.
     */
    String getNormalized();

    /**
     * One of the spellings reporters actually used, for display. Which one is
     * arbitrary but stable; the point of the row is the count beside it.
     */
    String getName();

    long getReportCount();

    LocalDateTime getFirstSuggestedAt();

    LocalDateTime getLastSuggestedAt();
}
