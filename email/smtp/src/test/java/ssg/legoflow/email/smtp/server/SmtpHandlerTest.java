package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SmtpHandler} interface default methods and factory implementations.
 */
class SmtpHandlerTest {

    @Test
    void testDefaultAcceptSender() {
        SmtpHandler handler = new SmtpHandler() {};
        assertThat(handler.acceptSender("alice@example.com")).isTrue();
        assertThat(handler.acceptSender(null)).isTrue();
    }

    @Test
    void testDefaultAcceptRecipient() {
        SmtpHandler handler = new SmtpHandler() {};
        assertThat(handler.acceptRecipient("bob@example.com", "alice@example.com")).isTrue();
    }

    @Test
    void testDefaultAcceptMessage() {
        SmtpHandler handler = new SmtpHandler() {};
        var data = "msg".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("s@test.com", List.of("r@test.com"), data, "m1");
        assertThat(handler.acceptMessage(envelope)).isTrue();
    }

    @Test
    void testDefaultAuthenticateReturnsFalse() {
        SmtpHandler handler = new SmtpHandler() {};
        assertThat(handler.authenticate("user", "pass")).isFalse();
    }

    // ===== acceptAll() factory =====

    @Test
    void testAcceptAllAuthenticatesEveryone() {
        SmtpHandler handler = SmtpHandler.acceptAll();
        assertThat(handler.authenticate("u", "p")).isTrue();
        assertThat(handler.authenticate(null, null)).isTrue();
    }

    @Test
    void testAcceptAllAcceptsSendersRecipients() {
        SmtpHandler handler = SmtpHandler.acceptAll();
        assertThat(handler.acceptSender("any@addr.com")).isTrue();
        assertThat(handler.acceptRecipient("rcpt@addr.com", "snd@addr.com")).isTrue();
    }

    // ===== forDomains() factory =====

    @Test
    void testForDomainsAcceptsMatchingDomain() {
        SmtpHandler handler = SmtpHandler.forDomains("example.com");
        assertThat(handler.acceptRecipient("user@example.com", null)).isTrue();
        assertThat(handler.acceptRecipient("user@other.com", null)).isFalse();
    }

    @Test
    void testForDomainsMultipleDomains() {
        SmtpHandler handler = SmtpHandler.forDomains("a.com", "b.org");
        assertThat(handler.acceptRecipient("u@a.com", null)).isTrue();
        assertThat(handler.acceptRecipient("u@d.com", null)).isFalse();
    }

    // ===== Standard reply factories =====

    @Test
    void testSmtpReplyGreeting() {
        var reply = SmtpReply.greeting("mail.example.com");
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).contains("mail.example.com");
    }

    @Test
    void testSmtpReplyClosing() {
        var reply = SmtpReply.closing("mail.example.com");
        assertThat(reply.code()).isEqualTo(221);
    }

    @Test
    void testSmtpReplyOk() {
        var reply = SmtpReply.ok();
        assertThat(reply.code()).isEqualTo(250);
        var replyWithId = SmtpReply.ok("msg-12345");
        assertThat(replyWithId.text()).contains("id=msg-12345");
    }

    @Test
    void testSmtpReplyAuth() {
        assertThat(SmtpReply.authChallenge("chal").code()).isEqualTo(334);
        assertThat(SmtpReply.authSuccess().code()).isEqualTo(235);
        assertThat(SmtpReply.authFailed().code()).isEqualTo(535);
    }

    @Test
    void testSmtpReplyErrors() {
        assertThat(SmtpReply.commandUnrecognized().code()).isEqualTo(500);
        assertThat(SmtpReply.syntaxError().code()).isEqualTo(501);
        assertThat(SmtpReply.notImplemented().code()).isEqualTo(502);
        assertThat(SmtpReply.badSequence().code()).isEqualTo(503);
        assertThat(SmtpReply.authRequired().code()).isEqualTo(530);
        assertThat(SmtpReply.mailboxNotFound().code()).isEqualTo(550);
        assertThat(SmtpReply.messageTooLarge().code()).isEqualTo(552);
        assertThat(SmtpReply.transactionFailed().code()).isEqualTo(554);
        assertThat(SmtpReply.mailboxBusy().code()).isEqualTo(450);
        assertThat(SmtpReply.mailboxSyntaxError().code()).isEqualTo(553);
    }

    @Test
    void testSmtpReplyStartInput() {
        var reply = SmtpReply.startInput();
        assertThat(reply.code()).isEqualTo(354);
        assertThat(reply.isIntermediate()).isTrue();
    }

    // ===== Constructor validation =====

    @Test
    void testConstructorRejectsInvalidCode() {
        assertThatThrownBy(() -> new SmtpReply(199, "text"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmtpReply(600, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorRejectsNullLines() {
        List<String> nullList = null;
        assertThatThrownBy(() -> SmtpReply.ofLines(250, nullList))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testConstructorRejectsEmptyLines() {
        assertThatThrownBy(() -> SmtpReply.ofLines(250, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== Enhanced code =====

    @Test
    void testSenderOkRecipientOk() {
        assertThat(SmtpReply.senderOk().code()).isEqualTo(250);
        assertThat(SmtpReply.recipientOk().code()).isEqualTo(250);
    }

    @Test
    void testOfFactories() {
        var reply1 = SmtpReply.of(200, "Custom OK");
        assertThat(reply1.code()).isEqualTo(200);
        var reply2 = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_MAIL_SYSTEM, "OK");
        assertThat(reply2.enhancedCode()).isEqualTo(EnhancedStatusCode.SUCCESS_MAIL_SYSTEM);
        var reply3 = SmtpReply.ofLines(250, List.of("First", "Second"));
        assertThat(reply3.lines()).hasSize(2);
    }

    @Test
    void testTextMustNotBeNull() {
        assertThatThrownBy(() -> new SmtpReply(250, (String) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testServiceUnavailable() {
        var reply = SmtpReply.serviceUnavailable("mail.test.com");
        assertThat(reply.code()).isEqualTo(421);
    }
}
