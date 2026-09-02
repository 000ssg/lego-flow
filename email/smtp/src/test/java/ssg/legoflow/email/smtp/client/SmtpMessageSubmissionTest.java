package ssg.legoflow.email.smtp.client;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.server.InMemoryMessageStore;
import ssg.legoflow.email.smtp.server.SmtpHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
class SmtpMessageSubmissionTest {

    @Test void testSendWithConfig() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            var result = MessageSubmission.send(config, "sender@example.com",
                    List.of("recipient@example.com"), "Subject: Test\r\n\r\nHello");
            assertThat(result.success()).isTrue();
            assertThat(result.code()).isEqualTo(250);
            TimeUnit.SECONDS.sleep(1);
            assertThat(store.getMessages()).hasSize(1);
        }
    }

    @Test void testSendSimpleWithSubjectAndBody() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            var result = MessageSubmission.sendSimple(config, "sender@example.com",
                    List.of("recipient@example.com"), "Test Subject", "Hello World");
            assertThat(result.success()).isTrue();
            TimeUnit.SECONDS.sleep(1);
            assertThat(store.getMessages()).hasSize(1);
        }
    }

    @Test void testSendFailureReturnsFalseResult() {
        var config = SmtpClientConfig.builder("127.0.0.1", 65535).build();
        var result = MessageSubmission.send(config, "sender@example.com",
                List.of("recipient@example.com"), "Subject: Test\r\n\r\nHello");
        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo(-1);
    }

    @Test void testSendWithMultipleRecipients() throws Exception {
        var store = new InMemoryMessageStore();
        try (var server = new ssg.legoflow.email.smtp.server.SmtpServer("localhost", 0)) {
            server.setMessageStore(store);
            server.setHandler(SmtpHandler.acceptAll());
            server.start();
            var config = SmtpClientConfig.builder("127.0.0.1", server.getPort()).build();
            var result = MessageSubmission.send(config, "sender@example.com",
                    List.of("r1@example.com", "r2@example.com"), "Subject: Test\r\n\r\nHi");
            assertThat(result.success()).isTrue();
            TimeUnit.SECONDS.sleep(1);
            var messages = store.getMessages();
            assertThat(messages).hasSize(1);
            var envelope = messages.get(0);
            assertThat(envelope.recipients()).containsExactly("r1@example.com", "r2@example.com");
        }
    }

    @Test void testDeliveryResultToString() {
        var success = new MessageSubmission.DeliveryResult(true, 250, "OK", null);
        assertThat(success.toString()).contains("success=true");
        var failure = new MessageSubmission.DeliveryResult(false, 550, "rejected",
                new SmtpException("error"));
        assertThat(failure.toString()).contains("success=false");
    }

    @Test void testDeliveryResultCode() {
        var result = new MessageSubmission.DeliveryResult(true, 250, "OK", null);
        assertThat(result.code()).isEqualTo(250);
    }
}
