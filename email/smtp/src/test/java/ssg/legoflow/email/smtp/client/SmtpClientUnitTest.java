package ssg.legoflow.email.smtp.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import ssg.legoflow.email.smtp.protocol.SmtpReply;
import static org.assertj.core.api.Assertions.*;
/**
 * Unit tests for {@link SmtpClient}, {@link SmtpConnection}, and related client classes.
 */
@DisplayName("SmtpClient Unit Tests")
class SmtpClientUnitTest {

    @Test
    void testClientRejectsNullConfig() {
        assertThatThrownBy(() -> new SmtpClient(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testClientThrowsIfNotConnected_send() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThatThrownBy(() -> client.send("a@b.com", java.util.List.of("c@d.com"), "msg"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not connected");
        }
    }

    @Test
    void testClientThrowsIfNotConnected_reset() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThatThrownBy(() -> client.reset())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testClientThrowsIfNotConnected_noop() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThatThrownBy(() -> client.noop())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testClientThrowsIfNotConnected_verify() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThatThrownBy(() -> client.verify("user"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testClientThrowsIfNotConnected_sendChunked() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThatThrownBy(() -> client.sendChunked("a@b.com", java.util.List.of("c@d.com"), "body".getBytes()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void testExtensionsReturnsEmptyWhenNotConnected() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThat(client.extensions()).isEmpty();
        }
    }

    @Test
    void testHasExtensionReturnsFalseWhenNotConnected() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThat(client.hasExtension(ssg.legoflow.email.smtp.protocol.SmtpExtension.SIZE)).isFalse();
        }
    }

    @Test
    void testIsAuthenticatedReturnsFalseWhenNotConnected() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            assertThat(client.isAuthenticated()).isFalse();
        }
    }

    @Test
    void testCloseWithNoConnection() throws Exception {
        var config = SmtpClientConfig.builder("localhost", 25).build();
        try (var client = new SmtpClient(config)) {
            // close() should not throw even if never connected
        }
    }

    @Test
    void testSmtpExceptionWithReply() {
        SmtpReply reply = SmtpReply.of(550, "User unknown");
        var ex = new SmtpException("MAIL FROM failed", reply);
        assertThat(ex.reply()).isEqualTo(reply);
        assertThat(ex.replyCode()).isEqualTo(550);
    }

    @Test
    void testSmtpExceptionWithCause() {
        var cause = new RuntimeException("Connection reset");
        var ex = new SmtpException("Network error", cause);
        assertThat(ex.reply()).isNull();
        assertThat(ex.replyCode()).isEqualTo(-1);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void testSmtpExceptionSimple() {
        var ex = new SmtpException("Generic error");
        assertThat(ex.reply()).isNull();
        assertThat(ex.replyCode()).isEqualTo(-1);
    }

    @Test
    void testDeliveryResultSuccessNoThrow() throws Exception {
        var result = new MessageSubmission.DeliveryResult(true, 250, "OK", null);
        assertThat(result.success()).isTrue();
        result.throwIfFailed(); // should not throw
    }

    @Test
    void testDeliveryResultFailureThrows() throws Exception {
        var ex = new SmtpException("rejected");
        var result = new MessageSubmission.DeliveryResult(false, 550, "rejected", ex);
        assertThat(result.success()).isFalse();
        assertThatThrownBy(() -> result.throwIfFailed())
                .isInstanceOf(SmtpException.class);
    }

    @Test
    void testDeliveryResultFailureNoCauseDoesNotThrow() throws Exception {
        var result = new MessageSubmission.DeliveryResult(false, 550, "rejected", null);
        // When exception is null, throwIfFailed should not throw
        result.throwIfFailed();
    }

    @Test
    void testSmtpConnectionRejectsNullConfig() {
        assertThatThrownBy(() -> new SmtpConnection(null))
                .isInstanceOf(NullPointerException.class);
    }
}
