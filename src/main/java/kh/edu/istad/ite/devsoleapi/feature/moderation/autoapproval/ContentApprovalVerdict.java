package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

/**
 * What the model decided about one submission.
 *
 * <p>The two questions are kept apart because they fail for different reasons
 * and a moderator reading the queue needs to know which one it was: a recipe
 * is perfectly safe and entirely off-topic, and an on-topic post can still be
 * abusive.
 *
 * @param onTopic    software development, security research, or the practice
 *                   around them
 * @param safe       no profanity, harassment, sexual content, or anything else
 *                   the platform would not put on a public page
 * @param confidence 0-100, how sure the model is of both answers together.
 *                   Below the threshold the post waits for a person, which is
 *                   what it would have done anyway.
 * @param reason     one short sentence for the moderation log, so a hold is
 *                   explainable to the author
 */
public record ContentApprovalVerdict(
        boolean onTopic,
        boolean safe,
        int confidence,
        String reason
) {
}
