package kh.edu.istad.ite.devsoleapi.feature.organization.researcher.enums;

/**
 * How far a company wants a researcher removed.
 *
 * <p>Named rather than a boolean flag, because the two are different decisions
 * and the screen offering them should say which is which. Taking somebody off
 * one confidential program is a judgement about that program; ending their
 * ability to report to the company at all is a judgement about the person, and
 * that is the one reached from a malicious upload.
 */
public enum ResearcherRemovalScope {

    /**
     * Withdraws every private-program invitation. The researcher can still
     * report to the company's public programs, which is the right outcome when
     * the objection is to their presence on one confidential engagement rather
     * than to them.
     */
    PRIVATE_PROGRAMS,

    /**
     * The above, and their standing with the company as well — so they can no
     * longer submit to its public programs either. What a company reaches for
     * when a researcher has uploaded something a scanner called malicious.
     */
    ENTIRE_COMPANY
}
