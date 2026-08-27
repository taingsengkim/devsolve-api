package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.common.mail.MailMessage;
import kh.edu.istad.ite.devsoleapi.common.mail.MailService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMailerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private NotificationRecipientRepository recipientRepository;

    @Mock
    private MailService mailService;

    @Test
    void aReportNotificationIsEmailedWithoutAnyoneChoosingIt() {
        noStoredPreference(NotificationType.REPORT);
        activeRecipient();

        mailer().email(notification(
                NotificationType.REPORT,
                "Your report was triaged",
                "Acme Security accepted your report."
        ));

        MailMessage message = sentMessage();
        assertEquals("researcher@example.com", message.to());
        assertEquals("Your report was triaged", message.subject());
        assertTrue(message.textBody().contains(
                "Acme Security accepted your report."
        ));
        assertTrue(message.htmlBody().contains(
                "https://www.devsolve.app/notifications"
        ));
        assertTrue(message.htmlBody().contains(
                "https://www.devsolve.app/settings/notifications"
        ));
        assertTrue(message.textBody().contains("Hi Rina"));
    }

    @Test
    void aCommentNotificationIsNotEmailedWithoutBeingAskedFor() {
        noStoredPreference(NotificationType.COMMENT);

        mailer().email(notification(
                NotificationType.COMMENT,
                "New comment",
                "Someone replied to you."
        ));

        // Stops before it even looks the recipient up: nothing to send means
        // nothing to load.
        verifyNoInteractions(mailService, recipientRepository);
    }

    @Test
    void anInvitationIsLeftToItsOwnMailerRatherThanSentTwice() {
        noStoredPreference(NotificationType.INVITATION);

        mailer().email(notification(
                NotificationType.INVITATION,
                "You have been invited to Acme Security",
                "Acme Owner invited you."
        ));

        verifyNoInteractions(mailService);
    }

    @Test
    void aTypeTheUserSwitchedOffStopsBeingEmailed() {
        storedPreference(NotificationType.REPORT, false);

        mailer().email(notification(
                NotificationType.REPORT,
                "Your report was triaged",
                "Acme Security accepted your report."
        ));

        verifyNoInteractions(mailService);
    }

    @Test
    void aTypeTheUserSwitchedOnStartsBeingEmailed() {
        storedPreference(NotificationType.COMMENT, true);
        activeRecipient();

        mailer().email(notification(
                NotificationType.COMMENT,
                "New comment",
                "Someone replied to you."
        ));

        assertEquals("New comment", sentMessage().subject());
    }

    @Test
    void aSuspendedAccountIsNotWrittenTo() {
        noStoredPreference(NotificationType.REPORT);
        when(recipientRepository.findRecipientById(USER_ID)).thenReturn(
                Optional.of(recipient(
                        "researcher@example.com",
                        "Rina Sok",
                        UserStatus.SUSPENDED
                ))
        );

        mailer().email(notification(
                NotificationType.REPORT,
                "Your report was triaged",
                "Acme Security accepted your report."
        ));

        verifyNoInteractions(mailService);
    }

    @Test
    void contentIsEscapedBeforeItReachesTheHtmlBody() {
        noStoredPreference(NotificationType.REPORT);
        activeRecipient();

        mailer().email(notification(
                NotificationType.REPORT,
                "Your report was triaged",
                "<script>alert('x')</script> said the reviewer"
        ));

        String htmlBody = sentMessage().htmlBody();
        assertFalse(htmlBody.contains("<script>"));
        assertTrue(htmlBody.contains("&lt;script&gt;"));
    }

    private NotificationMailer mailer() {
        return new NotificationMailer(
                preferenceRepository,
                recipientRepository,
                mailService,
                "https://www.devsolve.app",
                "/notifications",
                "/settings/notifications"
        );
    }

    private Notification notification(
            NotificationType type,
            String title,
            String content
    ) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .title(title)
                .content(content)
                .notifiableType(type)
                .notifiableId(UUID.randomUUID())
                .read(false)
                .build();
    }

    private void noStoredPreference(NotificationType type) {
        when(preferenceRepository.findByUserIdAndType(USER_ID, type))
                .thenReturn(Optional.empty());
    }

    private void storedPreference(
            NotificationType type,
            boolean emailEnabled
    ) {
        when(preferenceRepository.findByUserIdAndType(USER_ID, type))
                .thenReturn(Optional.of(new NotificationPreference(
                        USER_ID,
                        type,
                        emailEnabled
                )));
    }

    private void activeRecipient() {
        when(recipientRepository.findRecipientById(USER_ID)).thenReturn(
                Optional.of(recipient(
                        "researcher@example.com",
                        "Rina Sok",
                        UserStatus.ACTIVE
                ))
        );
    }

    private NotificationRecipientRepository.Recipient recipient(
            String email,
            String fullName,
            UserStatus status
    ) {
        return new NotificationRecipientRepository.Recipient() {
            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getFullName() {
                return fullName;
            }

            @Override
            public UserStatus getStatus() {
                return status;
            }
        };
    }

    private MailMessage sentMessage() {
        ArgumentCaptor<MailMessage> messageCaptor =
                ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }
}
