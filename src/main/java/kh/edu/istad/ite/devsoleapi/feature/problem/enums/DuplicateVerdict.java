package kh.edu.istad.ite.devsoleapi.feature.problem.enums;

/**
 * How an existing problem relates to the one somebody is drafting.
 *
 * <p>Three levels rather than a yes/no, because the useful answer usually is
 * not "this is a duplicate". Most of the value in showing somebody an older
 * problem is that it carries the answer to a variant of what they are about to
 * ask, and collapsing that into "duplicate" would either suppress it or
 * overstate it.
 *
 * <p>Declared in the order they are shown in, so the frontend can sort on the
 * enum and a new level in the middle later moves everything correctly.
 */
public enum DuplicateVerdict {

    /** The same underlying problem. Reading it answers the draft outright. */
    DUPLICATE,

    /**
     * The same cause wearing different clothes — another framework, another
     * version, another symptom of one bug. The answer needs adapting rather
     * than just reading.
     */
    NEAR_DUPLICATE,

    /** Not the same problem, but worth reading while stuck on this one. */
    RELATED
}
