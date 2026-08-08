package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MailEnvelopeTest {
    @Test void testCreateWithMessageId() {
        byte[] data = "From: sender@test.com\r\n\r\nHello".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("sender@test.com", List.of("recipient@test.com"), data, "<msg-1>");
        assertThat(envelope.sender()).isEqualTo("sender@test.com");
        assertThat(envelope.recipients()).containsExactly("recipient@test.com");
    }

    @Test void testMultipleRecipients() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com", "e@f.com"), data, "<id>");
        assertThat(envelope.recipients()).hasSize(2);
    }

    @Test void testDataAsUtf8String() {
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.dataAsString()).isEqualTo("Hello World");
    }

    @Test void testSize() {
        byte[] data = new byte[1024];
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.size()).isEqualTo(1024);
    }

    @Test void testReceivedAtNotNull() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.receivedAt()).isNotNull();
    }

    @Test void testToString() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.toString()).isNotBlank();
    }

    @Test void testDataIsCopy() {
        byte[] data = "original".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        data[0] = 'X';
        assertThat(envelope.dataAsString()).startsWith("original");
    }

    @Test void testRecipientsIsCopy() {
        List<String> rcpts = new ArrayList<>(List.of("a@b.com"));
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("x@y.com", rcpts, data, "<id>");
        rcpts.clear();
        assertThat(envelope.recipients()).containsExactly("a@b.com");
    }

    @Test void testMessageId() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<my-id>");
        assertThat(envelope.messageId()).isEqualTo("<my-id>");
    }

    @Test void testNullMailParamsReturnsEmpty() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.mailParams()).isNotNull();
    }

    @Test void testNullRcptParamsReturnsEmpty() {
        byte[] data = "msg".getBytes(StandardCharsets.UTF_8);
        var envelope = new MailEnvelope("a@b.com", List.of("c@d.com"), data, "<id>");
        assertThat(envelope.rcptParams("c@d.com")).isNotNull();
    }
}
