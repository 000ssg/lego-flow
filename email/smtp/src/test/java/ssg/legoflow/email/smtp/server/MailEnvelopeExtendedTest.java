package ssg.legoflow.email.smtp.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MailEnvelopeExtendedTest {

    @Test void envelopeWithMessageId() {
        var env = new MailEnvelope("sender@test.com", List.of("rcpt@test.com"), 
                "data".getBytes(), "my-msg-id");
        assertThat(env.messageId()).isEqualTo("my-msg-id");
        assertThat(env.sender()).isEqualTo("sender@test.com");
        assertThat(env.recipients()).containsExactly("rcpt@test.com");
    }

    @Test void envelopeDataAsString() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "Hello World".getBytes(), "m1");
        assertThat(env.dataAsString()).isEqualTo("Hello World");
    }

    @Test void envelopeSize() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "12345".getBytes(), "m2");
        assertThat(env.size()).isEqualTo(5);
    }

    @Test void envelopeReceivedAtNonNull() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "data".getBytes(), "m3");
        assertThat(env.receivedAt()).isNotNull();
    }

    @Test void envelopeMailParams() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "data".getBytes(), "m4");
        assertThat(env.mailParams()).isNotNull();
    }

    @Test void envelopeRcptParams() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "data".getBytes(), "m5");
        assertThat(env.rcptParams("r@t.com")).isNotNull();
    }

    @Test void envelopeToStringNonNull() {
        var env = new MailEnvelope("s@t.com", List.of("r@t.com"), 
                "data".getBytes(), "m6");
        assertThat(env.toString()).isNotBlank();
    }
}
