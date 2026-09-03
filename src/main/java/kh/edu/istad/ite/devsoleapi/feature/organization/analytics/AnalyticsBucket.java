package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * How wide one point on the submission trend is.
 *
 * <p>The unit names match {@code date_trunc}'s: the same value is passed to it
 * and used here to walk the buckets, so the zero-filled series and the query
 * results have to land on the same boundaries.
 *
 * <p>Labels are formatted in {@link Locale#ENGLISH} so a server whose default
 * locale changes does not change what a chart axis reads.
 */
public enum AnalyticsBucket {

    DAY("day"),

    /** Monday-based, matching {@code date_trunc('week', …)}. */
    WEEK("week"),

    MONTH("month");

    private static final DateTimeFormatter DAY_LABEL =
            DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_LABEL =
            DateTimeFormatter.ofPattern("'Week of' d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final String dateTruncUnit;

    AnalyticsBucket(String dateTruncUnit) {
        this.dateTruncUnit = dateTruncUnit;
    }

    /** The first argument to {@code date_trunc}. */
    public String dateTruncUnit() {
        return dateTruncUnit;
    }

    /** The start of the bucket the given day falls in. */
    public LocalDate floor(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            );
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    /** The start of the bucket after the one beginning at {@code start}. */
    public LocalDate next(LocalDate start) {
        return switch (this) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
    }

    /** {@code 2026-09}, {@code 2026-W36} or {@code 2026-09-03}. */
    public String periodKey(LocalDate start) {
        return switch (this) {
            case DAY -> start.toString();
            case WEEK -> "%d-W%02d".formatted(
                    start.get(IsoFields.WEEK_BASED_YEAR),
                    start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            );
            case MONTH -> "%d-%02d".formatted(
                    start.getYear(),
                    start.getMonthValue()
            );
        };
    }

    /** What the chart axis shows. */
    public String label(LocalDate start) {
        return switch (this) {
            case DAY -> DAY_LABEL.format(start);
            case WEEK -> WEEK_LABEL.format(start);
            case MONTH -> MONTH_LABEL.format(start);
        };
    }
}
