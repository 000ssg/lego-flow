package ssg.legoflow.email.smtp.dsn;

import org.junit.jupiter.api.Test;
import ssg.legoflow.email.smtp.protocol.EnhancedStatusCode;
import static org.assertj.core.api.Assertions.*;

class DsnGeneratorTest {
    @Test void testGenerateBounce() {
        String dsn = DsnGenerator.generateBounce(
                "mta.example.com",
                "sender@example.com",
                "recipient@baddest.com",
                EnhancedStatusCode.PERM_BAD_DEST_MAILBOX,
                "Mailbox not found",
                null);
        assertThat(dsn).isNotBlank();
    }

    @Test void testGenerateBounceWithOriginalMsg() {
        String dsn = DsnGenerator.generateBounce(
                "mta.example.com",
                "sender@example.com",
                "recipient@baddest.com",
                EnhancedStatusCode.PERM_REFUSED,
                "Message refused",
                "From: a@b.com\r\nTo: c@d.com\r\n\r\nHello");
        assertThat(dsn).isNotBlank();
    }

    @Test void testGenerateDelay() {
        String dsn = DsnGenerator.generateDelay(
                "mta.example.com",
                "sender@example.com",
                "recipient@slowdest.com",
                "Temporary failure",
                null);
        assertThat(dsn).isNotBlank();
    }

    @Test void testGenerateSuccess() {
        String dsn = DsnGenerator.generateSuccess(
                "mta.example.com",
                "sender@example.com",
                "recipient@gooddest.com",
                null);
        assertThat(dsn).isNotBlank();
    }

    @Test void testGenerateWithOriginalMsgInDelay() {
        String dsn = DsnGenerator.generateDelay(
                "mta.example.com",
                "sender@example.com",
                "recipient@slowdest.com",
                "Temporary failure",
                "From: a@b.com\r\n\r\nbody");
        assertThat(dsn).isNotBlank();
    }

    @Test void testGenerateWithOriginalMsgInSuccess() {
        String dsn = DsnGenerator.generateSuccess(
                "mta.example.com",
                "sender@example.com",
                "recipient@gooddest.com",
                "From: a@b.com\r\n\r\nbody");
        assertThat(dsn).isNotBlank();
    }
}
