package kh.edu.istad.ite.devsoleapi.common.mail;

/**
 * One outbound email, already rendered.
 *
 * <p>Both bodies are carried because mail clients pick: {@code htmlBody} is
 * what almost everyone sees, {@code textBody} is what plain-text clients,
 * screen readers and spam filters read instead. Sending only HTML is a
 * reliable way to score as spam.
 *
 * @param to        a single recipient address
 * @param subject   subject line, plain text
 * @param textBody  plain-text alternative
 * @param htmlBody  HTML body; every value interpolated into it must already
 *                  be escaped by whoever composed it
 */
public record MailMessage(
        String to,
        String subject,
        String textBody,
        String htmlBody
) {
}
