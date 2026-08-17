package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

/**
 * Who raised a flag.
 *
 * <p>Worth recording because the two are worth different amounts to a
 * moderator. A person took the trouble to report something; the filter only
 * matched a word, and matched it without knowing who it was aimed at or
 * whether it was aimed at anybody. A queue that cannot tell them apart ends
 * up being read as if every entry were the weaker kind.
 */
public enum FlagSource {
    USER,
    AUTOMATED
}
