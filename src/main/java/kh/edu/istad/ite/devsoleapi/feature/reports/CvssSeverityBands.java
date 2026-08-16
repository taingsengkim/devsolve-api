package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * The CVSS v3.1 qualitative rating scale.
 *
 * <p>A reporter supplies a severity claim and, optionally, the CVSS vector and
 * score behind it. Left unchecked those two disagree freely — a 2.1 filed as
 * CRITICAL — and triage has to litigate arithmetic instead of the finding. The
 * scale is fixed by the specification, so the disagreement is decidable here.
 */
public final class CvssSeverityBands {

    /**
     * Base vectors only. A temporal or environmental vector describes a
     * different score than the base one recorded beside it, and accepting it
     * would put a number in the column that the vector does not produce.
     */
    private static final Pattern VECTOR_PATTERN = Pattern.compile(
            "^CVSS:3\\.[01]"
                    + "/AV:[NALP]/AC:[LH]/PR:[NLH]/UI:[NR]"
                    + "/S:[UC]/C:[NLH]/I:[NLH]/A:[NLH]$"
    );

    private static final BigDecimal LOW_FLOOR = new BigDecimal("0.1");
    private static final BigDecimal MEDIUM_FLOOR = new BigDecimal("4.0");
    private static final BigDecimal HIGH_FLOOR = new BigDecimal("7.0");
    private static final BigDecimal CRITICAL_FLOOR = new BigDecimal("9.0");

    private CvssSeverityBands() {
    }

    public static boolean isWellFormedVector(String vector) {
        return vector != null && VECTOR_PATTERN.matcher(vector).matches();
    }

    /**
     * The rating a score carries on its own, independent of what was claimed.
     */
    public static Severity ratingFor(BigDecimal score) {
        if (score.compareTo(LOW_FLOOR) < 0) {
            return Severity.NONE;
        }
        if (score.compareTo(MEDIUM_FLOOR) < 0) {
            return Severity.LOW;
        }
        if (score.compareTo(HIGH_FLOOR) < 0) {
            return Severity.MEDIUM;
        }
        if (score.compareTo(CRITICAL_FLOOR) < 0) {
            return Severity.HIGH;
        }
        return Severity.CRITICAL;
    }
}
