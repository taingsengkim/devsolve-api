package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

/**
 * What actually happened on a feed row.
 *
 * <p>Stored on the row rather than inferred by the reader. A client used to
 * have to guess the event from which nested object came back non-null, which
 * made every new kind of entry a breaking change for anybody who had guessed
 * differently.
 *
 * <p>{@link #RECOGNITION_AWARDED} and {@link #BOUNTY_AWARDED} are written when
 * a triager recognises a resolved report, and which of the two it is depends
 * on whether that report also carries a payout. Those rows carry a
 * recognition; {@link #REPORT_RESOLVED} and {@link #REPORT_DISCLOSED} do not,
 * because a report is fixed or published whether or not anybody was credited
 * for it.
 *
 * <p>Nothing is written when a report is submitted or merely confirmed. Both
 * would announce, on a feed served to anonymous callers, that a named program
 * has a live vulnerability nobody has fixed yet. A resolution is safe to
 * publish for the opposite reason: it says the thing is closed.
 */
public enum HacktivityEventType {

    /** A resolved report was recognised, with no money attached. */
    RECOGNITION_AWARDED,

    /** A recognised report that also carries a reward with an amount on it. */
    BOUNTY_AWARDED,

    /** A report became publicly disclosed. Carries no recognition. */
    REPORT_DISCLOSED,

    /** An organization fixed a reported vulnerability. No recognition. */
    REPORT_RESOLVED
}
