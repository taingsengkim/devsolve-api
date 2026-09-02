package kh.edu.istad.ite.devsoleapi.feature.search;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fields every document carries, whatever index it lives in.
 *
 * <p>This shared envelope is what lets one endpoint search programs, showcases,
 * problems, organizations and people together and hand back a single list. The
 * alternative — a result shape per index — would push the work of knowing five
 * document layouts onto every caller, including the search box that just wants
 * something to draw.
 *
 * <p>Indexes add whatever else they want on top. The envelope is a floor, not a
 * ceiling: {@link #TYPE} and {@link #SLUG} are what a client needs to link to a
 * hit, and the rest of a document is returned alongside it for the callers that
 * want to render more than a line of text.
 */
public final class SearchDocuments {

    /** Which index the hit came from, duplicated into the document so a multi-index result set can be sorted and grouped without tracking where each hit came from. */
    public static final String TYPE = "type";

    public static final String ID = "id";

    /** The name of the thing. Highlighted in results. */
    public static final String TITLE = "title";

    /** The line under the title — a handle, an author, a slug. */
    public static final String SUBTITLE = "subtitle";

    /** The prose. Cropped around the match rather than returned whole. */
    public static final String BODY = "body";

    public static final String IMAGE_URL = "imageUrl";

    /**
     * How this thing is addressed in a URL — a program's handle, an
     * organization's slug, a person's username. The things that have no such
     * name of their own, showcases and problems, repeat their id here.
     *
     * <p>A ready-made link would be friendlier, and is deliberately not what
     * this is. The routes belong to the web app, this service has never been
     * told them, and a guessed one is worse than none: it would render as a
     * link and lead nowhere. A client pairs this with {@link #TYPE} and builds
     * the route it actually has.
     */
    public static final String SLUG = "slug";

    public static final String CREATED_AT = "createdAt";

    /**
     * The row's {@code updated_at} as epoch seconds. Sortable on every index
     * because the synchronizer reads the newest one back to work out where the
     * last run stopped.
     */
    public static final String UPDATED_AT = "updatedAt";

    /**
     * A program policy or a problem description can run to tens of kilobytes.
     * Past a few thousand characters the extra text is not what anybody is
     * searching for, and it is paid for on every document write, every index
     * rebuild and every hit returned.
     */
    private static final int MAX_BODY_LENGTH = 8_000;

    private SearchDocuments() {
    }

    /**
     * Starts a document with the shared fields filled in. Mutable on purpose —
     * the caller goes on to add the fields specific to its index.
     */
    public static Map<String, Object> envelope(
            String type,
            UUID id,
            String title,
            String subtitle,
            String body,
            String imageUrl,
            String slug
    ) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(ID, id.toString());
        document.put(TYPE, type);
        document.put(TITLE, blankToNull(title));
        document.put(SUBTITLE, blankToNull(subtitle));
        document.put(BODY, clip(body));
        document.put(IMAGE_URL, blankToNull(imageUrl));
        document.put(SLUG, slug);
        return document;
    }

    /**
     * Timestamps go in as epoch seconds rather than as strings: Meilisearch
     * sorts and filters numbers, and would treat an ISO string as text to match
     * against.
     *
     * <p>UTC on the way in and UTC on the way out. The columns behind these are
     * {@code TIMESTAMP} with no zone, so the zone chosen here is arbitrary and
     * the only thing that matters is that one is chosen once.
     */
    public static Long epochSeconds(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toEpochSecond(ZoneOffset.UTC);
    }

    public static Long epochSeconds(Instant timestamp) {
        return timestamp == null ? null : timestamp.getEpochSecond();
    }

    public static String nameOf(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String clip(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_BODY_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_BODY_LENGTH);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
