package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The window the dashboard is looking at, and everything derived from it.
 *
 * <p>A closed set rather than a pair of free dates: every number on the page
 * is compared against the window immediately before it, and it keeps the cache
 * key to five ranges per program instead of one entry per pair of timestamps.
 */
public enum AnalyticsTimeRange {

    LAST_30_DAYS("30d", Period.ofDays(30), AnalyticsBucket.DAY),
    LAST_90_DAYS("90d", Period.ofDays(90), AnalyticsBucket.WEEK),
    LAST_6_MONTHS("6m", Period.ofMonths(6), AnalyticsBucket.MONTH),
    LAST_YEAR("1y", Period.ofYears(1), AnalyticsBucket.MONTH),

    /**
     * Everything the organization has ever received. {@code length} is null:
     * there is no period before all of history, so no figure on the page
     * carries a change against one.
     */
    ALL_TIME("all", null, AnalyticsBucket.MONTH);

    /** What a request that names no range gets. */
    public static final AnalyticsTimeRange DEFAULT = LAST_6_MONTHS;

    /**
     * Where {@link #ALL_TIME} starts. Earlier than any report this platform
     * can hold, so one query serves every range rather than two that differ
     * only in whether they have a lower bound.
     */
    private static final LocalDateTime BEGINNING_OF_TIME =
            LocalDateTime.of(1970, 1, 1, 0, 0);

    private final String wireValue;
    private final Period length;
    private final AnalyticsBucket bucket;

    AnalyticsTimeRange(
            String wireValue,
            Period length,
            AnalyticsBucket bucket
    ) {
        this.wireValue = wireValue;
        this.length = length;
        this.bucket = bucket;
    }

    /**
     * @param value the {@code timeRange} query parameter. Null or blank asks
     *              for {@link #DEFAULT}; anything unrecognised is refused with
     *              400 naming the accepted values, rather than quietly
     *              answering for a window the caller did not ask for.
     */
    public static AnalyticsTimeRange fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(range -> range.wireValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown timeRange '" + value + "'. Accepted values: "
                                + accepted()
                ));
    }

    public static String accepted() {
        return Arrays.stream(values())
                .map(AnalyticsTimeRange::wireValue)
                .collect(Collectors.joining(", "));
    }

    public String wireValue() {
        return wireValue;
    }

    public AnalyticsBucket bucket() {
        return bucket;
    }

    /** Whether a preceding window exists to compare this one against. */
    public boolean hasPreviousPeriod() {
        return length != null;
    }

    public LocalDateTime startOf(LocalDateTime now) {
        return length == null ? BEGINNING_OF_TIME : now.minus(length);
    }

    /**
     * The start of the window immediately before this one. Only meaningful
     * when {@link #hasPreviousPeriod()}.
     */
    public LocalDateTime previousStartOf(LocalDateTime now) {
        if (length == null) {
            return BEGINNING_OF_TIME;
        }
        return startOf(now).minus(length);
    }
}
