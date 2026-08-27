package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.mail.MailMessage;
import kh.edu.istad.ite.devsoleapi.common.mail.MailService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrgRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrganizationInvitationMailerTest {

    private static final String TOKEN = "6f1a9d3e-2b7c-4a10-9f55-0e2c1d4b8a63";

    @Mock
    private MailService mailService;

    @Test
    void invitationEmailLinksToTheAcceptPageCarryingTheToken() {
        OrganizationInvitationMailer mailer = new OrganizationInvitationMailer(
                mailService,
                "https://devsolve.io/",
                "invitations"
        );

        mailer.onInvitation(event("Acme Corp", OrgRole.MANAGER));

        MailMessage message = sentMessage();
        String acceptUrl = "https://devsolve.io/invitations/" + TOKEN;
        assertEquals("member@acme.com", message.to());
        assertTrue(message.subject().contains("Acme Corp"));
        assertTrue(message.htmlBody().contains(acceptUrl));
        assertTrue(message.textBody().contains(acceptUrl));
        assertTrue(message.textBody().contains("as a manager"));
        assertTrue(message.textBody().contains("Acme Owner"));
        assertTrue(message.textBody().contains("expires on"));
    }

    @Test
    void companyNamesCannotInjectMarkupIntoTheHtmlBody() {
        OrganizationInvitationMailer mailer = new OrganizationInvitationMailer(
                mailService,
                "https://devsolve.io",
                "/invitations"
        );

        mailer.onInvitation(
                event("Acme <script>alert('x')</script>", OrgRole.MEMBER)
        );

        String htmlBody = sentMessage().htmlBody();
        assertFalse(htmlBody.contains("<script>"));
        assertTrue(htmlBody.contains("Acme &lt;script&gt;"));
    }

    @Test
    void withoutAFrontendUrlTheEmailPointsAtTheInAppInvitation() {
        OrganizationInvitationMailer mailer = new OrganizationInvitationMailer(
                mailService,
                "",
                "/invitations"
        );

        mailer.onInvitation(event("Acme Corp", OrgRole.MEMBER));

        MailMessage message = sentMessage();
        assertFalse(message.htmlBody().contains("Accept invitation</a>"));
        assertFalse(message.textBody().contains(TOKEN));
        assertTrue(
                message.textBody().contains("your DevSolve notifications")
        );
    }

    private OrganizationInvitationEmailEvent event(
            String organizationName,
            OrgRole role
    ) {
        return new OrganizationInvitationEmailEvent(
                UUID.randomUUID(),
                organizationName,
                "Acme Owner",
                "member@acme.com",
                "Acme Member",
                role,
                TOKEN,
                LocalDateTime.now().plusDays(7)
        );
    }

    private MailMessage sentMessage() {
        ArgumentCaptor<MailMessage> messageCaptor =
                ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService).send(messageCaptor.capture());
        return messageCaptor.getValue();
    }
}
