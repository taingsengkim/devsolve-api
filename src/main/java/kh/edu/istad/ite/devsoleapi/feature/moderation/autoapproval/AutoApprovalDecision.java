package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * Publish it, or leave it where it is.
 *
 * <p>There is no third outcome. Auto-rejection is deliberately not a thing the
 * model can do: a wrong hold costs the author the wait they already expected,
 * and a wrong rejection buries a legitimate post behind a verdict nobody
 * reviewed.
 *
 * @param reason one sentence for the log, which is where the model's own
 *               wording belongs and the only place it is quoted
 * @param hold   what the author is told, or {@code null} on an approval. Kept
 *               apart from {@code reason} because the two have different
 *               audiences — see {@link AutoApprovalHold}
 */
public record AutoApprovalDecision(
        boolean approved,
        String reason,
        AutoApprovalHold hold
) {

    public static AutoApprovalDecision approve(String reason) {
        return new AutoApprovalDecision(true, reason, null);
    }

    public static AutoApprovalDecision hold(
            AutoApprovalHold hold,
            String reason
    ) {
        return new AutoApprovalDecision(false, reason, hold);
    }
}
