package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

/**
 * What actually happened on a feed row.
 *
 * <p>Stored on the row rather than inferred by the reader. A client used to
 * have to guess the event from which nested object came back non-null, which
 * made every new kind of entry a breaking change for anybody who had guessed
 * differently.
 *
 * <p>{@link #RECOGNITION_AWARDED} and {@link #BOUNTY_AWARDED} are the two the
 * platform writes today — every hacktivity row is created when a triager
 * recognises a resolved report, and which of the two it is depends on whether
 * that report also carries a payout. {@link #REPORT_DISCLOSED} and
 * {@link #REPORT_RESOLVED} are declared because they are part of the published
 * filter vocabulary and the disclosure and resolution paths are the obvious
 * next writers; filtering on them today is well-formed and returns nothing.
 */
public enum HacktivityEventType {

    /** A resolved report was recognised, with no money attached. */
    RECOGNITION_AWARDED,

    /** A recognised report that also carries a reward with an amount on it. */
    BOUNTY_AWARDED,

    /** Reserved: a report became publicly disclosed. Not written yet. */
    REPORT_DISCLOSED,

    /** Reserved: a report reached the resolved state. Not written yet. */
    REPORT_RESOLVED
}
