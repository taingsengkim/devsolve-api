package kh.edu.istad.ite.devsoleapi.common.mail;

public interface MailService {

    /**
     * Sends one email over SMTP.
     *
     * <p>Never throws. Email is always a side channel here — the membership,
     * the invitation, the notification are already committed by the time
     * anything is sent — so a dead SMTP host must not take the request with
     * it. Callers that care whether the mail left get the answer as the
     * return value instead of an exception.
     *
     * @return true when the message was handed to the SMTP server; false when
     *         mail is not configured, the address is missing, or the send
     *         failed (all three are logged)
     */
    boolean send(MailMessage message);
}
