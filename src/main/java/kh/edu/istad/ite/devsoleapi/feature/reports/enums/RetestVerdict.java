package kh.edu.istad.ite.devsoleapi.feature.reports.enums;

import jakarta.persistence.EnumeratedValue;

/**
 * What a researcher found when they re-ran their own proof of concept against
 * a deployed fix.
 *
 * <p>Deliberately only two values. A retest answers one question — is it still
 * exploitable — and a third option like "partially fixed" turns that answer
 * into a negotiation, which is what the report thread and a further retest are
 * for.
 */
public enum RetestVerdict {
    VERIFIED_FIXED("verified_fixed"),
    STILL_VULNERABLE("still_vulnerable");

    @EnumeratedValue
    private final String databaseValue;

    RetestVerdict(String databaseValue) {
        this.databaseValue = databaseValue;
    }
}
