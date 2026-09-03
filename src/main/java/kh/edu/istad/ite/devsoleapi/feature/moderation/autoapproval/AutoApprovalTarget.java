package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * What auto-approval can be switched on for.
 *
 * <p>One switch per kind rather than one for the platform: a showcase is a
 * finished write-up and a problem is somebody stuck, and an administrator who
 * trusts the model on one has no reason to be forced into trusting it on the
 * other.
 */
public enum AutoApprovalTarget {

    PROBLEM,
    SHOWCASE
}
