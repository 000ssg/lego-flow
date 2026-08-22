package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SmtpReply}.
 */
class SmtpReplyTest {

    @Test
    void testSingleLineReply() {
        var reply = new SmtpReply(250, "OK");
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.text()).isEqualTo("OK");
        assertThat(reply.lines()).containsExactly("OK");
        assertThat(reply.isMultiLine()).isFalse();
        assertThat(reply.enhancedCode()).isNull();
    }

    @Test
    void testSingleLineWithEnhanced() {
        var reply = new SmtpReply(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.enhancedCode()).isEqualTo(EnhancedStatusCode.SUCCESS_OTHER);
        assertThat(reply.text()).isEqualTo("OK");
    }

    @Test
    void testMultiLineReply() {
        var reply = SmtpReply.ofLines(250, List.of("mail.example.com", "SIZE 10485760", "PIPELINING", "OK"));
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.lines()).hasSize(4);
        assertThat(reply.isMultiLine()).isTrue();
        assertThat(reply.text()).isEqualTo("mail.example.com");
    }

    @Test
    void testInvalidCodeLow() {
        assertThatThrownBy(() -> new SmtpReply(100, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidCodeHigh() {
        assertThatThrownBy(() -> new SmtpReply(600, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptyLines() {
        assertThatThrownBy(() -> SmtpReply.ofLines(250, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullLines() {
        assertThatThrownBy(() -> SmtpReply.ofLines(250, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIsSuccess() {
        assertThat(SmtpReply.of(200, "OK").isSuccess()).isTrue();
        assertThat(SmtpReply.of(250, "OK").isSuccess()).isTrue();
        assertThat(SmtpReply.of(299, "OK").isSuccess()).isTrue();
        assertThat(SmtpReply.of(354, "text").isSuccess()).isFalse();
        assertThat(SmtpReply.of(450, "text").isSuccess()).isFalse();
        assertThat(SmtpReply.of(550, "text").isSuccess()).isFalse();
    }

    @Test
    void testIsIntermediate() {
        assertThat(SmtpReply.of(334, "challenge").isIntermediate()).isTrue();
        assertThat(SmtpReply.of(354, "go ahead").isIntermediate()).isTrue();
        assertThat(SmtpReply.of(250, "OK").isIntermediate()).isFalse();
    }

    @Test
    void testIsTransientFailure() {
        assertThat(SmtpReply.of(421, "busy").isTransientFailure()).isTrue();
        assertThat(SmtpReply.of(450, "try later").isTransientFailure()).isTrue();
        assertThat(SmtpReply.of(550, "denied").isTransientFailure()).isFalse();
    }

    @Test
    void testIsPermanentFailure() {
        assertThat(SmtpReply.of(500, "error").isPermanentFailure()).isTrue();
        assertThat(SmtpReply.of(550, "denied").isPermanentFailure()).isTrue();
        assertThat(SmtpReply.of(450, "busy").isPermanentFailure()).isFalse();
    }

    @Test
    void testIsNegative() {
        assertThat(SmtpReply.of(450, "busy").isNegative()).isTrue();
        assertThat(SmtpReply.of(550, "denied").isNegative()).isTrue();
        assertThat(SmtpReply.of(250, "OK").isNegative()).isFalse();
    }

    @Test
    void testFactoryGreeting() {
        var reply = SmtpReply.greeting("mail.example.com");
        assertThat(reply.code()).isEqualTo(220);
        assertThat(reply.text()).contains("mail.example.com");
    }

    @Test
    void testFactoryClosing() {
        var reply = SmtpReply.closing("mail.example.com");
        assertThat(reply.code()).isEqualTo(221);
        assertThat(reply.text()).contains("mail.example.com");
    }

    @Test
    void testFactoryOk() {
        assertThat(SmtpReply.ok().code()).isEqualTo(250);
        assertThat(SmtpReply.ok().text()).isEqualTo("OK");
    }

    @Test
    void testFactoryOkWithId() {
        var reply = SmtpReply.ok("msg123");
        assertThat(reply.code()).isEqualTo(250);
        assertThat(reply.text()).contains("msg123");
    }

    @Test
    void testFactorySenderOk() {
        assertThat(SmtpReply.senderOk().code()).isEqualTo(250);
    }

    @Test
    void testFactoryRecipientOk() {
        assertThat(SmtpReply.recipientOk().code()).isEqualTo(250);
    }

    @Test
    void testFactoryAuthSuccess() {
        assertThat(SmtpReply.authSuccess().code()).isEqualTo(235);
    }

    @Test
    void testFactoryAuthChallenge() {
        var reply = SmtpReply.authChallenge("dGVzdA==");
        assertThat(reply.code()).isEqualTo(334);
        assertThat(reply.text()).isEqualTo("dGVzdA==");
    }

    @Test
    void testFactoryStartInput() {
        assertThat(SmtpReply.startInput().code()).isEqualTo(354);
    }

    @Test
    void testFactoryErrorReplies() {
        assertThat(SmtpReply.commandUnrecognized().code()).isEqualTo(500);
        assertThat(SmtpReply.syntaxError().code()).isEqualTo(501);
        assertThat(SmtpReply.notImplemented().code()).isEqualTo(502);
        assertThat(SmtpReply.badSequence().code()).isEqualTo(503);
        assertThat(SmtpReply.authRequired().code()).isEqualTo(530);
        assertThat(SmtpReply.authFailed().code()).isEqualTo(535);
        assertThat(SmtpReply.mailboxNotFound().code()).isEqualTo(550);
        assertThat(SmtpReply.messageTooLarge().code()).isEqualTo(552);
        assertThat(SmtpReply.mailboxSyntaxError().code()).isEqualTo(553);
        assertThat(SmtpReply.transactionFailed().code()).isEqualTo(554);
    }

    @Test
    void testFactoryTransientReplies() {
        assertThat(SmtpReply.serviceUnavailable("host").code()).isEqualTo(421);
        assertThat(SmtpReply.mailboxBusy().code()).isEqualTo(450);
    }

    @Test
    void testToStringSingleLine() {
        var reply = SmtpReply.of(250, "OK");
        assertThat(reply.toString()).isEqualTo("250 OK");
    }

    @Test
    void testToStringWithEnhanced() {
        var reply = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        assertThat(reply.toString()).contains("2.0.0");
    }

    @Test
    void testEquality() {
        var a = SmtpReply.of(250, "OK");
        var b = SmtpReply.of(250, "OK");
        var c = SmtpReply.of(250, "Different");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testEqualityWithEnhanced() {
        var a = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        var b = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
        var c = SmtpReply.of(250, EnhancedStatusCode.SUCCESS_ADDRESS, "OK");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }
}
