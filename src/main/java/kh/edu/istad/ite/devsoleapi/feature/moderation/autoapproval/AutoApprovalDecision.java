package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * Publish it, or leave it where it is.
 *
 * <p>There is no third outcome. Auto-rejection is deliberately not a thing the
 * model can do: a wrong hold costs the author the wait they already expected,
 * and a wrong rejection buries a legitimate post behind a verdict nobody
 * reviewed.
 */
public record AutoApprovalDecision(boolean approved, String reason) {

    public static AutoApprovalDecision approve(String reason) {
        return new AutoApprovalDecision(true, reason);
    }

    public static AutoApprovalDecision hold(String reason) {
        return new AutoApprovalDecision(false, reason);
    }
}
