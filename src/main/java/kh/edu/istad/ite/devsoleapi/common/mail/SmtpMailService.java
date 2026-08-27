package kh.edu.istad.ite.devsoleapi.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class SmtpMailService implements MailService {

    /**
     * Optional on purpose. Spring Boot only builds a {@link JavaMailSender}
     * once {@code spring.mail.host} is set, and a developer running the API
     * without an SMTP server should still get a working app — one that logs
     * the mail it would have sent — rather than a context that refuses to
     * start.
     */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    private final String host;
    private final String from;
    private final String fromName;

    public SmtpMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:DevSolve}") String fromName
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.host = host == null ? "" : host.trim();
        this.from = from == null ? "" : from.trim();
        this.fromName = fromName == null ? "" : fromName.trim();
    }

    @Override
    public boolean send(MailMessage message) {
        if (message == null
                || message.to() == null
                || message.to().isBlank()) {
            log.warn("Refusing to send an email with no recipient");
            return false;
        }

        JavaMailSender mailSender = host.isBlank()
                ? null
                : mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn(
                    "SMTP is not configured (spring.mail.host is empty) — "
                            + "\"{}\" for {} was not sent",
                    message.subject(),
                    message.to()
            );
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            // Plain text first, HTML second: this order is what makes the two
            // a multipart/alternative pair rather than an attachment.
            helper.setText(message.textBody(), message.htmlBody());
            if (!from.isBlank()) {
                helper.setFrom(from, fromName);
            }
            mailSender.send(mimeMessage);
            log.info("Sent \"{}\" to {}", message.subject(), message.to());
            return true;
        } catch (MessagingException
                 | UnsupportedEncodingException
                 | MailException exception) {
            log.error(
                    "Failed to send \"{}\" to {}",
                    message.subject(),
                    message.to(),
                    exception
            );
            return false;
        }
    }
}
